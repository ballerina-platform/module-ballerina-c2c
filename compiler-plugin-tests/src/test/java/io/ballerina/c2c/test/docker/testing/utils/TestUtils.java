package io.ballerina.c2c.test.docker.testing.utils;

import org.testng.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class TestUtils {
    private static final Boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.getDefault())
            .contains("win");

    /**
     * Replace varying strings in the content.
     * @param firstString   the first string to replace
     * @param endString     the end string to replace
     * @param content       the content to replace the strings
     * @return            the modified content
     */
    public static String replaceVaryingString(String firstString, String endString, String content) {
        String modifiedContent = content;
        int firstPos = modifiedContent.indexOf(firstString);
        int lastPos = -1;
        if (firstPos >= 0) {
            firstPos = firstPos + firstString.length();
            lastPos = modifiedContent.indexOf(endString, firstPos);
        }
        while (firstPos != -1) {
            modifiedContent = modifiedContent.substring(0, firstPos) + "*****" + modifiedContent.substring(lastPos);
            firstPos = modifiedContent.indexOf(firstString, firstPos);
            if (firstPos >= 0) {
                firstPos = firstPos + firstString.length();
                lastPos = modifiedContent.indexOf(endString, firstPos);
            }
        }
        return modifiedContent;
    }

    /**
     * Assert the file content.
     * @param expectedOutputFileName    the name of the text file to compare the output with
     * @param actualOutput      the actual output to compare
     * @param commandOutputsDir the directory where the output file is located
     * @throws IOException    if an I/O error occurs reading from the file
     */
    public static void assertOutput(String expectedOutputFileName, Path commandOutputsDir, String actualOutput)
            throws IOException {
        String fileContent = Files.readString(commandOutputsDir.resolve(expectedOutputFileName));
        if (isWindows) {
            fileContent = fileContent.replaceAll("\r\n|\r", "\n");
            actualOutput = actualOutput.replaceAll("\r\n|\r", "\n");
        }
        //TODO: test again after rebasing lang repo
        if(actualOutput.contains("Test execution time :")) {
            actualOutput = replaceVaryingString("Test execution time :", "s", actualOutput);
        }
        // Remove warning: Detected conflicting jar files: from output
        // TODO: remove the following warning once the PR is merged
        if (actualOutput.contains("warning: Detected conflicting jar files:")) {
            actualOutput = actualOutput.substring(0, actualOutput.indexOf("warning: Detected conflicting jar files:"));
        }
        Assert.assertEquals(actualOutput.stripTrailing(), fileContent.stripTrailing());
    }
}
