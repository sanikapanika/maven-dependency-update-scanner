package io.jenkins.plugins.plaincredentials;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Util;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import java.util.UUID;

@NameWith(StringCredentials.NameProvider.class)
public interface StringCredentials extends StandardCredentials {

    @Nonnull
    Secret getSecret();

    class NameProvider extends CredentialsNameProvider<StringCredentials> {
        @Override
        public String getName(StringCredentials c) {
            String descr = Util.fixEmptyAndTrim(c.getDescription());
            String ID = c.getId();
            return descr != null ? descr : (!isUUID(ID) ? ID : Messages.StringCredentials_string_credentials());
        }

        private static boolean isUUID(String ID) {
            try {
                UUID.fromString(ID);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
