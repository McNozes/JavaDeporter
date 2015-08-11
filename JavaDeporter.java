import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Scanner;
import java.util.EnumSet;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream ;
import java.io.ByteArrayOutputStream ;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitor;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.*;

// Uses two passes
public class JavaDeporter
{
    static private void processFile(Path path) throws IOException {
        new SingleFileProcessor(path);
    }

    static private class TreeProcessor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path path,BasicFileAttributes attrs) {
            try {
                processFile(path);
            } catch (Exception e) {
                System.err.println("IO error in " + path);
            }
            return CONTINUE;
        }
    }

    public static void main(String[] args) throws IOException {
        for (String file : args) {
            Path path = Paths.get(file);
            if (Files.isDirectory(path)) {
                EnumSet<FileVisitOption> opts;
                opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
                TreeProcessor tp = new TreeProcessor();
                Files.walkFileTree(path,opts,Integer.MAX_VALUE,tp);
            } else {
                processFile(path);
            }
        }
    }
}


class SingleFileProcessor
{
    private static Pattern className; 
    private static Pattern classNameEnd; 
    private static Pattern importLine;
    private static Pattern javaExtension;
    private Map<String,String> imports = new HashMap<>();
    private Set<String> classes = new HashSet<>();

    static {
        className = Pattern.compile("[A-Z][a-z1-9A-Z_]+");
        classNameEnd = Pattern.compile(className.pattern() + "; *$");
        importLine = Pattern.compile(" *import +(\\.|\\w)+[;] *$");
        javaExtension = Pattern.compile(".*[.]java$");
    }


    SingleFileProcessor(Path path) throws IOException {
        if (!javaExtension.matcher(path.toString()).matches()) {
            return;
        }
        byte[] data = getAsByteArray(path);
        getClasses(data);
        int count = filterImports();
        if (count != 0) {
            writeFile(path,data);
            System.out.println("Deleted " + count + " lines (" + path + ")");
        } 
    }

    private static boolean isImportLine(String line) {
        return importLine.matcher(line).matches();
    }

    private void getClasses(byte[] arr) throws IOException {
        InputStream input = new ByteArrayInputStream(arr);
        BufferedReader reader = 
            new BufferedReader(new InputStreamReader(input));
        String line;
        while ((line = reader.readLine()) != null) {
            Scanner scan = new Scanner(line);
            String name;
            while ((name = scan.findInLine(className)) != null) {
                if (isImportLine(line)) {
                    imports.put(name,line);
                } else {
                    classes.add(name);
                }
            }
        }
    }

    private int filterImports() {
        int count = 0;
        for (String name : imports.keySet()) {
            if (!classes.contains(name)) {
                imports.remove(name);
                count++;
            } 
        }
        return count;
    }

    private void writeFile(Path path,byte[] buff)
        throws IOException
    {
        InputStream input = new ByteArrayInputStream(buff);
        BufferedReader reader = 
            new BufferedReader(new InputStreamReader(input));

        PrintWriter writer = new PrintWriter(Files.newOutputStream(path));

        String line;
        while ((line = reader.readLine()) != null) {
            if (isImportLine(line)) {
                String name = new Scanner(line)
                    .findInLine(classNameEnd)
                    .replaceAll(";","");
                if (imports.containsKey(name)) {
                    writer.println(line);
                }
            } else {
                writer.println(line);
            }
        }
        writer.flush();
        writer.close();
    }

    private static byte[] getAsByteArray(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int data; (data = input.read()) != -1;) {
            output.write(data);
        }
        input.close();
        return output.toByteArray();
    }
}
