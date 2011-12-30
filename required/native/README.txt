This directory contains pre-built jars that contain the results of the "native" project.

Each directory is an artifactId. The groupId is com.jiminger

You can install these into your repository, or just copy them into it since the directory contains all of the metadata. You would copy them into a directory called "path_to_local_repo/com/jiminger." On windows by default everything will end up in your User directory.

For example, you would have a jar file here:

C:\Users\[user]\.m2\repository\com\jiminger\windows-x86_64-jiminger\1.0-SNAPSHOT\windows-x86_64-jiminger-1.0-SNAPSHOT.jar

The jar file contains a dll.

This is being done because building these is a pain.

