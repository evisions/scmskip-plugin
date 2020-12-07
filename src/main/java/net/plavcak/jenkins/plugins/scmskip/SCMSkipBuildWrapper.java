package net.plavcak.jenkins.plugins.scmskip;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SCMSkipBuildWrapper extends BuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(SCMSkipBuildWrapper.class.getName());

    private transient SCMSkipMatcher skipMatcher;
    private boolean deleteBuild;
    private String skipPattern;
    private boolean headOnly;

    @DataBoundConstructor
    public SCMSkipBuildWrapper(boolean deleteBuild, String skipPattern, boolean headOnly) {
        this.deleteBuild = deleteBuild;
        this.skipPattern = skipPattern;
        // if (this.skipPattern == null) {
        //     this.skipPattern = SCMSkipConstants.DEFAULT_PATTERN;
        // }
        this.headOnly = headOnly;
        this.skipMatcher = new SCMSkipMatcher(getSkipPattern());
        this.skipMatcher.setHeadOnly(isHeadOnly());
    }

    public boolean isDeleteBuild() {
        return deleteBuild;
    }

    @DataBoundSetter
    public void setDeleteBuild(boolean deleteBuild) {
        this.deleteBuild = deleteBuild;
    }

    public String getSkipPattern() {
        if (this.skipPattern == null || this.skipPattern.isEmpty()) {
            return getDescriptor().getSkipPattern();
        }
        return skipPattern;
    }

    @DataBoundSetter
    public void setSkipPattern(String skipPattern) {
        if (skipPattern.equals(getDescriptor().getSkipPattern()))
            skipPattern = null;
        this.skipPattern = skipPattern;
        this.skipMatcher.setPattern(getSkipPattern());
    }

    public boolean isHeadOnly() {
        return headOnly;
    }

    @DataBoundSetter
    public void setHeadOnly(boolean headOnly) {
        this.headOnly = headOnly;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException {

        if(SCMSkipTools.inspectChangeSet(build, skipMatcher, listener)) {
            SCMSkipTools.tagRunForDeletion(build, deleteBuild);

            try {
                SCMSkipTools.stopBuild(build);
            } catch (AbortException e) {
                throw e;
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "SCM Skip Build Wrapper", e);
                }
            }
        } else {
            SCMSkipTools.tagRunForDeletion(build, false);
        }

        return new Environment() { };
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        private String skipPattern = SCMSkipConstants.DEFAULT_PATTERN;

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "SCM Skip";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            req.bindJSON(this, json.getJSONObject("scmSkip"));
            save();
            return true;
        }

        public String getSkipPattern() {
            return skipPattern;
        }

        @DataBoundSetter
        public void setSkipPattern(String skipPattern) {
            this.skipPattern = skipPattern;
        }
    }
}
