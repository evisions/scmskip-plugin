package net.plavcak.jenkins.plugins.scmskip;

import hudson.AbortException;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SCMSkipTools {

    private static final Logger LOGGER = Logger.getLogger(SCMSkipTools.class.getName());

    /**
     * Deletes given build.
     * @param build build to delete
     * @throws IOException if build cannot be deleted
     */
    public static void deleteBuild(AbstractBuild build) throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Deleting AbstractBuild: '" + build.getId() + "'");
        }
        build.delete();
        AbstractProject project = build.getProject();
        project.updateNextBuildNumber(build.getNumber());
        project.save();
    }

    /**
     * Deletes given build.
     * @param run build to delete
     * @throws IOException if build cannot be deleted
     */
    public static void deleteRun(Run<?,?> run) throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Deleting Run: '" + run.getId() + "'");
        }
        run.delete();
        Job job = run.getParent();
        job.updateNextBuildNumber(run.number);
        job.save();
    }

    public static void tagRunForDeletion(Run<?,?> run, boolean deleteBuild) throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,"Build '" + run.getDisplayName() + "' set to delete: " + deleteBuild);
        }
        run.addAction(new SCMSkipDeleteEnvironmentAction(deleteBuild));
        run.save();
    }

    public static boolean isBuildToDelete(Run<?,?> run) {
        SCMSkipDeleteEnvironmentAction action = run.getAction(SCMSkipDeleteEnvironmentAction.class);
        return action != null && action.isDeleteBuild();
    }

    /**
     * Inspect build for matched pattern in changelog.
     * @param run current build
     * @param matcher matcher object
     * @param listener runtime listener
     * @return true if matched otherwise returns false
     */
    public static boolean inspectChangeSet(Run run, SCMSkipMatcher matcher, TaskListener listener) {
        if (run instanceof WorkflowRun) {
            return inspectChangeSet(((WorkflowRun) run).getChangeSets(), matcher, listener.getLogger());
        } else if(run instanceof AbstractBuild) {
            return inspectChangeSet(((AbstractBuild<?,?>) run).getChangeSets(), matcher, listener.getLogger());
        }

        return false;
    }

    private static boolean inspectChangeSet(List<ChangeLogSet<?>> changeLogSets, SCMSkipMatcher matcher, PrintStream logger) {
        if (changeLogSets.isEmpty()) {
            logEmptyChangeLog(logger);
            return false;
        }

        ChangeLogSet<?> changeLogSet = changeLogSets.get(changeLogSets.size()-1);

        if (changeLogSet.isEmptySet()) {
            logEmptyChangeLog(logger);
        }

        ChangeLogSet.Entry matchedEntry = null;
        ChangeLogSet.Entry lastEntry = null;
        boolean headOnly = matcher.getHeadOnly();

        for (Object entry : changeLogSet.getItems()) {
            if (headOnly) {
                if (entry instanceof ChangeLogSet.Entry) {
                    lastEntry = (Entry) entry;
                }
                continue;
            }
            if (entry instanceof ChangeLogSet.Entry && inspectChangeSetEntry((Entry) entry, matcher)) {
                matchedEntry = (Entry) entry;
                break;
            }
        }

        String commitMessage;

        if (headOnly && lastEntry != null) {
            if (lastEntry instanceof ChangeLogSet.Entry && inspectChangeSetEntry(lastEntry, matcher)) {
                matchedEntry = lastEntry;
            }
            commitMessage = lastEntry.getMsg();
        }
        else {
            commitMessage = combineChangeLogMessages(changeLogSet);
        }

        if (matchedEntry == null) {
            logger.println("SCM Skip: headOnly = " + String.valueOf(headOnly) + ", Pattern "
                    + matcher.getPattern().pattern()
                    + " NOT matched on message: "
                    + commitMessage);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "SCM Skip: Pattern "
                        + matcher.getPattern().pattern()
                        + " NOT matched on message: "
                        + commitMessage);
            }
        } else {
            logger.println("SCM Skip: headOnly = " + String.valueOf(headOnly) + ", Pattern "
                + matcher.getPattern().pattern()
                + " matched on message: "
                + commitMessage);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "SCM Skip: Pattern "
                    + matcher.getPattern().pattern()
                    + " matched on message: "
                    + commitMessage);
            }
        }

        return matchedEntry != null;
    }

    public static void stopBuild(Run<?, ?> run) throws AbortException, IOException, ServletException {
        run.setDescription("SCM Skip - build skipped");
        run.setResult(Result.ABORTED);
        run.save();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,"Stopping build: '" + run.getId() +"'");
        }

        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun)run;
            workflowRun.doStop();
        } else if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            build.doStop();
        } else {
            throw new AbortException("SCM Skip: Build has been skipped due to SCM Skip Plugin!");
        }
    }

    private static boolean inspectChangeSetEntry(ChangeLogSet.Entry entry, SCMSkipMatcher matcher) {
        return matcher.match(entry.getMsg());
    }

    private static String combineChangeLogMessages(ChangeLogSet<?> changeLogSet) {
        return Arrays.stream(changeLogSet.getItems())
            .map(i -> ((Entry) i).getMsg())
            .collect(Collectors.joining(" "));
    }

    private static void logEmptyChangeLog(PrintStream logger) {
        logger.println("SCM Skip: Changelog is empty!");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Changelog is empty!");
        }
    }
}
