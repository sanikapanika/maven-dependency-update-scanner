package io.jenkins.plugins.scanner;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.plaincredentials.StringCredentials;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DependencyScannerBuilder extends Builder implements SimpleBuildStep {

    private final String slackChannel;
    private final String slackWorkspace;
    private final String credentialsId;

    @DataBoundConstructor
    public DependencyScannerBuilder(String slackChannel, String slackWorkspace, String credentialsId) {
        this.slackChannel = slackChannel;
        this.slackWorkspace = slackWorkspace;
        this.credentialsId = credentialsId;
    }

    @Exported
    public String getSlackChannel() {
        return this.slackChannel;
    }

    @Exported
    public String getSlackWorkspace() {
        return this.slackWorkspace;
    }

    @Exported
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        Map<String, String> dependencies = this.executeScan("mvn -f " + filePath.getRemote() + "/pom.xml versions:display-dependency-updates", taskListener.getLogger());
        StringCredentials creds = CredentialsProvider.findCredentialById(
                this.credentialsId,
                StringCredentials.class,
                run,
                URIRequirementBuilder.fromUri("https://" + slackWorkspace + ".slack.com").build()
        );

        this.sendMessage(creds, dependencies, taskListener.getLogger());

    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckSlackChannel(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("Set channel here");
            }
            if (value.length() < 4) {
                return FormValidation.error("opet mesiđ");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String slackChannel,
                @QueryParameter String credentialsId,
                @QueryParameter String slackWorkspace
        ) {

            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result
                    .includeEmptyValue()
                    .includeMatchingAs(
                            item instanceof Queue.Task
                                    ? Tasks.getAuthenticationOf((Queue.Task) item)
                                    : ACL.SYSTEM,
                            item,
                            StringCredentials.class,
                            URIRequirementBuilder.fromUri("https://" + slackWorkspace + ".slack.com").build(),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class))
                    )
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter String slackChannel,
                @QueryParameter String slackWorkspace,
                @QueryParameter String value
        ) {
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            value = Util.fixEmptyAndTrim(value);
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("You must provide creds");
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                return FormValidation.warning("Cannot validate expression based credentials");
            }
            slackChannel = Util.fixEmptyAndTrim(slackChannel);
            if (StringUtils.isBlank(slackChannel)) {
                return FormValidation.error("Must provide channel to post to");
            }
            if (StringUtils.isBlank(slackWorkspace)) {
                return FormValidation.warning("Are you sure you are not on a private workspace (check before .slack.com)");
            }


            return FormValidation.ok();
        }

        private static StandardCredentials lookupCredentials(@CheckForNull Item project, String credentialId, String uri) {
            return (credentialId == null) ? null : CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(uri).build()),
                    CredentialsMatchers.withId(credentialId));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }

    private Map<String, String> executeScan(String command, PrintStream printStream) throws IOException {

        List<String> dependenciesToUpdate = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader stdInput = new BufferedReader(
                    new InputStreamReader(p.getInputStream())
            );

            List<String> lines = stdInput.lines().collect(Collectors.toList());
            StringBuilder current = new StringBuilder();

            for (String line : lines) {
                if ((line.contains("...") && !line.contains("Scanning for projects")) || line.contains("->")) {
                    current.append(line.replace("[INFO]", ""));
                }
                if (current.toString().contains("->")) {
                    dependenciesToUpdate.add(current.toString());
                    current = new StringBuilder();
                }
            }

            List<String> newFormatted = new ArrayList<>();

            for (String line : dependenciesToUpdate) {
                String trimmed = line.trim();
                String[] strings = trimmed.split("[ ]", 3);
                for (String s : strings) {
                    if (!s.contains("...")) {
                        newFormatted.add(s);
                    }
                }
            }

            Map<String, String> dependencyMap = new HashMap<>();
            for (int i = 0; i < newFormatted.size(); i++) {
                //                              get key                                     get value
                dependencyMap.put("`" + newFormatted.get(i).trim() + "`", "`" + newFormatted.get(++i).trim() + "`");
            }

            return dependencyMap;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void sendMessage(StringCredentials creds, Map<String, String> text, PrintStream logger) {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String strippedText = objectMapper.writeValueAsString(text).replaceAll("[{}]", "");

            HttpClient httpClient = HttpClient.newHttpClient();
            Map<String, Object> payload = new HashMap<String, Object>() {{
                put("channel", slackChannel);
                put("text", strippedText);
            }};
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonPayload);
            HttpRequest request = HttpRequest.newBuilder(
                    new URI("https://" + slackWorkspace + ".slack.com/api/chat.postMessage")
            ).headers(
                    "Authorization", "Bearer " + creds.getSecret().getPlainText(),
                    "Content-Type", "application/json"
            ).POST(
                    bodyPublisher
            ).build();

            HttpResponse response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new Exception("Bad response from slack api, message: " + response.body());
            }
        } catch (Throwable e) {
            logger.println(e.getMessage());
        }
    }
}
