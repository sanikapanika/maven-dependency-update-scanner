package io.jenkins.plugins.plaincredentials.impl;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import io.jenkins.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class StringCredentialsImpl extends BaseStandardCredentials implements StringCredentials {

    private static final long serialVersionUID = 4239232115673493707L;

    private final @Nonnull Secret secret;

    @DataBoundConstructor
    public StringCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id, @CheckForNull String description, @Nonnull Secret secret) {
        super(scope, id, description);
        this.secret = secret;
    }

    @Nonnull
    @Override
    public Secret getSecret() {
        return secret;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.StringCredentialsImpl_secret_text();
        }
    }
}
