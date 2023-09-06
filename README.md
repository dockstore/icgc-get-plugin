# icgc-get-plugin
Dockstore icgc file provisioning plugin.  Requires Docker installed.  Last tested with Docker version 17.03.0-ce, build 3a232c8
No longer maintained since the icgc-get client has been archived and deprecated

## Usage

The icgc-get plugin is capable of downloading files by calling out to an installed copy of the [icgc-get-client](http://docs.icgc.org/cloud/icgc-get).

```
$ cat test.icgc-get.json
{
  "input_file": {
        "class": "File",
        "path": "icgc-get://FI509397"
    },
    "output_file": {
        "class": "File",
        "path": "/tmp/md5sum.txt"
    }
}

$ dockstore tool launch --entry  quay.io/briandoconnor/dockstore-tool-md5sum  --json test.icgc-get.json
Creating directories for run of Dockstore launcher at: ./datastore//launcher-2ebce330-2a44-4a3a-9d6d-55c152a5c38e
Provisioning your input files to your local machine
Downloading: #input_file from icgc-get://FI509397 into directory: /home/gluu/dockstore/dockstore-client/./datastore/launcher-423d8d83-e6b0-418d-8a09-0e29003ac55f/inputs/0aafce03-f893-4ce2-b97f-f2c36215f162
Calling on plugin io.dockstore.provision.ICGCGetPlugin$ICGCGetProvision to provision icgc-get://FI509397
...
```


## Configuration

Download the plugin with `dockstore plugin download` by adding an entry to ~/.dockstore/plugins.json similar to the following
```
[
  {
    "name": "dockstore-file-icgc-get-plugin",
    "version": "0.0.4"
  },
  {
    "name": "dockstore-file-s3-plugin",
    "version": "0.0.3"
  },
  {
    "name": "dockstore-file-synapse-plugin",
    "version": "0.0.5"
  }
]
```

This plugin gets configuration information from the following structure in ~/.dockstore/config

```
[dockstore-file-icgc-get-plugin]
client = /usr/bin/icgc-get
config-file-location = /home/usr/.icgc-get/config.yaml
```

Set the client location to your own icgc-get client and also make sure that the configuration file is available at the config-file-location.


