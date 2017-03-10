/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.provision;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

/**
 * @author gluu
 */
public class ICGCGetPlugin extends Plugin {
    private static final Logger LOG = LoggerFactory.getLogger(ICGCGetPlugin.class);

    public ICGCGetPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        // for testing the development mode
        if (RuntimeMode.DEVELOPMENT.equals(wrapper.getRuntimeMode())) {
            System.out.println(StringUtils.upperCase("ICGCStorageClientPlugin development mode"));
        }
    }

    @Override
    public void stop() {
        System.out.println("ICGCGetPlugin.stop()");
    }

    @Extension
    public static class ICGCGetProvision implements ProvisionInterface {

        private static final String CLIENT_LOCATION = "client";
        private static final String CONFIG_FILE_LOCATION = "config-file-location";
        private static final String DEFAULT_CLIENT = "/usr/bin  /icgc-get";
        private static final String DEFAULT_CONFIGURATION = System.getProperty("user.home") + "/.icgc-get/config.yaml";
        private String client;
        private String configLocation;

        private Map<String, String> config;

        public void setConfiguration(Map<String, String> map) {
            this.config = map;
        }

        public Set<String> schemesHandled() {
            return new HashSet<>(Lists.newArrayList("icgc-get"));
        }

        /**
         * This sets the s3cmd client and s3 config file based on the dockstore config file and defaults
         */
        private void setConfigAndClient() {
            if (config == null) {
                LOG.error("You are missing a dockstore config file");
            }
            if (config.containsKey(CLIENT_LOCATION)) {
                setClient(config.get(CLIENT_LOCATION));
            } else {
                setClient(DEFAULT_CLIENT);
            }
            if (config.containsKey(CONFIG_FILE_LOCATION)) {
                setConfigLocation(config.get(CONFIG_FILE_LOCATION));
            } else {
                setConfigLocation(DEFAULT_CONFIGURATION);
            }
        }

        //
        // Downloads directory will look something like:
        // .staging
        // {{Object ID folder}}
        //     - logs
        //     - {{File Name file}}
        //

        /**
         * @param sourcePath  The scheme for icgc-get (icgc-get://FI509397)
         * @param destination The destination where the file is supposed to be (includes the filename like /home/user/icgc-get/downloads/file.txt)
         * @return Whether download was successful or not
         */
        public boolean downloadFrom(String sourcePath, Path destination) {
            String fileID = null;
            setConfigAndClient();

            // ambiguous how to reference icgc-get files, rip off these kinds of headers
            String prefix = "icgc-get://";
            if (sourcePath.startsWith(prefix)) {
                fileID = sourcePath.substring(prefix.length());
            }

            // default layout saves to original_file_name/object_id
            // file name is the directory and object id is actual file name
            String downloadDir = destination.getParent().toFile().getAbsolutePath() + "/tmp";
            createDirectory(downloadDir);
            String command = client + " --config " + configLocation + " download " + fileID + " --output " + downloadDir;
            if (executeConsoleCommand(command)) {
                moveFiles(downloadDir, destination);
            } else {
                LOG.error("Could not download file.");
            }
            return true;
        }

        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            throw new UnsupportedOperationException("ICGC Get does not support upload");
        }

        private boolean createDirectory(String directoryPath) {
            File theDir = new File(directoryPath);
            boolean result = false;
            if (!theDir.exists()) {
                try {
                    result = theDir.mkdir();
                    System.out.println("Tmp directory created.");
                } catch (SecurityException e) {
                    System.err.println("Could not create directory due to security reasons.");
                }
            } else {
                System.out.println("Tmp directory already exists.");
            }
            return result;
        }

        private boolean moveFiles(String downloadDir, Path destination) {
            // List of files at the destination directory
            String[] list = Paths.get(downloadDir).toFile().list();
            assert list != null;
            // File created at the destination
            for (String folder : list) {
                // Skip hidden directories
                if (folder.contains(".")) {
                    continue;
                }
                String s1 = downloadDir + "/" + folder;
                Path path = Paths.get(s1);
                String[] files = path.toFile().list();
                assert files != null;
                for (String file : files) {
                    // Skip the logs directory
                    if (file.equals("logs")) {
                        continue;
                    }
                    Path downloadedFileFileObj = Paths.get(path + "/" + file);
                    try {
                        Files.copy(downloadedFileFileObj, destination, StandardCopyOption.REPLACE_EXISTING);
                        LOG.info("File copied to destination.");
                        return true;
                    } catch (IOException ioe) {
                        LOG.error(ioe.getMessage());
                        throw new RuntimeException("Could not move input file: ", ioe);
                    }
                }

            }
            return false;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public void setConfigLocation(String configLocation) {
            this.configLocation = configLocation;
        }
        /**
         * Given a process, it will print out its stdout and stderr
         *
         * @param ps The process for print out
         * @return True if there's no error message, false if there is an error message
         */
        private boolean printCommandConsole(Process ps) {
            java.util.Scanner s = new java.util.Scanner(ps.getErrorStream()).useDelimiter("\\A");
            String errorString = s.hasNext() ? s.next() : "";
            LOG.warn("Error String: " + errorString);

            java.util.Scanner s2 = new java.util.Scanner(ps.getInputStream()).useDelimiter("\\A");
            String inputString = s2.hasNext() ? s2.next() : "";
            LOG.info("Input String: " + inputString);
            return (errorString.isEmpty());
        }

        /**
         * Executes the string command given
         *
         * @param command The command to execute
         * @return True if command was successfully execute without error, false otherwise.
         */
        private boolean executeConsoleCommand(String command) {
            Runtime rt = Runtime.getRuntime();
            try {
                Process ps = rt.exec(command);
                try {
                    ps.waitFor();
                } catch (InterruptedException e) {
                    LOG.error("Command got interrupted: " + command + " " + e);
                }
                return printCommandConsole(ps);
            } catch (IOException e) {
                LOG.error("Could not execute command: " + command + " " + e);
                throw new RuntimeException("Could not execute command: " + command);
            }
        }
    }
}

