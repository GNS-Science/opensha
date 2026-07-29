package org.opensha.commons.util;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitVersion {

    final String filePrefix;
    final File baseDirectory;

    public GitVersion() {
        this(new File(""), "/build");
    }

    public GitVersion(File baseDirectory, String filePrefix) {
        this.baseDirectory = baseDirectory.getAbsoluteFile();
        this.filePrefix = filePrefix;
    }

    public static String first(List<String> lines) {
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return null;
    }

    public static List<String> execute(String[] command, File directory) {
        // A missing working directory is reported by the JVM as "Cannot run program ... error=2,
        // No such file or directory" — indistinguishable from a missing git binary, and alarming
        // in a log. Check first so an absent source tree (the normal case in a deployed
        // container, which carries the jar but no checkout) is one quiet line instead.
        if (directory != null && !directory.isDirectory()) {
            System.err.println("Skipping " + String.join(" ", command)
                    + " : not a directory: " + directory);
            return null;
        }
        try {
            Process p = Runtime.getRuntime().exec(command, null, directory);
            int exit = p.waitFor();
            if (exit != 0) {
                System.err.println("Command " + String.join(" ", command)
                        + " exited " + exit + " in " + directory);
                return null;
            }
            return FileUtils.loadStream(p.getInputStream());
        } catch (Exception e) {
            System.err.println("Could not execute " + String.join(" ", command)
                    + " in " + directory + " : " + e.getMessage());
            return null;
        }
    }

    public String loadGitHash() throws IOException {
        String gitHashFile = filePrefix + ".githash";
        try {
            URL url = GitVersion.class.getResource(gitHashFile);
            if (url != null) {
                String gitHash = first(FileUtils.loadFile(url));
                if (gitHash != null) {
                    return gitHash;
                }
            }
        } catch (FileNotFoundException x) {
            System.out.println(gitHashFile + " resource not found");
        }

        String[] command = {"git", "rev-parse", "HEAD"};
        return first(execute(command, baseDirectory));
    }

    public String loadGitBranch() throws IOException {
        String gitBranchFileName = filePrefix + ".gitbranch";
        try {
            URL url = GitVersion.class.getResource(gitBranchFileName);
            if (url != null) {
                String branch = first(FileUtils.loadFile(url));
                if (branch != null) {
                    return branch;
                }
            }
        } catch (FileNotFoundException x) {
            System.out.println(gitBranchFileName + " resource not found.");
        }

        // Must run against baseDirectory, like loadGitHash and loadGitRemote. This used to use
        // the process cwd, which meant the branch of whatever repository happened to launch the
        // JVM got reported as this repository's branch — wrong data rather than absent data.
        String[] command = {"git", "rev-parse", "--abbrev-ref", "HEAD"};
        return first(execute(command, baseDirectory));
    }

    public String loadGitRemote() throws IOException {
        String gitBranchFileName = filePrefix + ".gitremoteurl";
        try {
            URL url = GitVersion.class.getResource(gitBranchFileName);
            if (url != null) {
                String branch = first(FileUtils.loadFile(url));
                if (branch != null) {
                    return branch;
                }
            }
        } catch (FileNotFoundException x) {
            System.out.println(gitBranchFileName + " resource not found.");
        }

        // Asks origin directly, matching build-git.gradle. The previous chain walked
        // name-rev -> branch.<name>.remote -> remote.<name>.url, which yields nothing whenever
        // HEAD sits on a tag: name-rev returns "tags/...", which is not a branch, so the middle
        // lookup fails and the last command becomes the literal `git config remote.null.url`.
        String[] command = {"git", "remote", "get-url", "origin"};
        return first(execute(command, baseDirectory));
    }

    public Date loadBuildDate() throws IOException {
        URL url = GitVersion.class.getResource(filePrefix + ".date");
        if (url == null) {
            return null;
        }

        for (String line : FileUtils.loadFile(url)) {
            if (NumberUtils.isParsable(line)) {
                long date = Long.parseLong(line);
                if (date > 0) {
                    return new Date(date);
                }
            }
        }
        return null;
    }

    public Map<String, String> getMap() {
        Map<String, String> result = new HashMap<>();
        try {
            String gitHash = loadGitHash();
            if (gitHash != null) {
                result.put("gitHash", gitHash);
            }
        } catch (IOException x) {
        }

        try {
            String branch = loadGitBranch();
            if (branch != null) {
                result.put("branch", branch);
            }
        } catch (IOException x) {
        }

        try {
            Date buildDate = loadBuildDate();
            if (buildDate != null) {
            String buildTime = loadBuildDate().toString();
                result.put("buildTime", buildTime);
            }
        } catch (IOException x) {
        }

        try {
            String remoteUrl = loadGitRemote();
            if (remoteUrl != null) {
                result.put("remoteUrl", remoteUrl);
            }
        } catch (IOException x) {
        }

        return result;
    }

    public static void main(String[] args) throws IOException {
        GitVersion git = new GitVersion();
        System.out.println(git.loadGitBranch());
        System.out.println(git.loadGitRemote());
        System.out.println(git.loadGitHash());
        System.out.println(git.loadBuildDate());
    }

}
