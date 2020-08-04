package org.ballerinax.kubernetes.utils;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.nio.file.Path;

/**
 * Toml Resolver class.
 */
public class TomlResolver {

    private File cloudTomlFile;

    public TomlResolver(Path cloudFilePath) {
        cloudTomlFile = cloudFilePath.toFile();
    }

    public Toml getToml() {
        return new Toml().read(cloudTomlFile);
    }
}
