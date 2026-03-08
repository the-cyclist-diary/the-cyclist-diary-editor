package com.github.thecyclistdiary.maps.cli;

import com.github.thecyclistdiary.maps.action.GitAwareGpxToMapWalker;
import com.github.thecyclistdiary.maps.action.GitHelper;
import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@QuarkusMain(name = "polyline-cli")
public class PolylineGeneratorCommand implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printHelp();
            return 0;
        }

        String command = args[0];
        
        if ("generate-polylines".equals(command)) {
            return generatePolylines(args);
        } else {
            Log.error("Unknown command: " + command);
            printHelp();
            return 1;
        }
    }

    private int generatePolylines(String... args) throws Exception {
        // Parse arguments
        Path contentPath = Paths.get("content"); // default
        boolean fullScan = false;
        Path gitRepo = Paths.get(".");

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--path", "-p" -> {
                    if (i + 1 < args.length) {
                        contentPath = Paths.get(args[++i]);
                    }
                }
                case "--full-scan", "-f" -> fullScan = true;
                case "--git-repo", "-g" -> {
                    if (i + 1 < args.length) {
                        gitRepo = Paths.get(args[++i]);
                    }
                }
                case "--help", "-h" -> {
                    printGenerateHelp();
                    return 0;
                }
            }
        }

        // Validate paths
        if (!Files.exists(contentPath)) {
            Log.error("Content path does not exist: " + contentPath);
            return 1;
        }

        if (!Files.isDirectory(contentPath)) {
            Log.error("Content path is not a directory: " + contentPath);
            return 1;
        }

        // Execute generation
        Log.info("=".repeat(80));
        Log.info("Polyline Generator CLI");
        Log.info("=".repeat(80));
        Log.info("Content path: %s".formatted(contentPath.toAbsolutePath()));
        Log.info("Git repository: %s".formatted(gitRepo.toAbsolutePath()));
        Log.info("Mode: %s".formatted(fullScan ? "FULL SCAN" : "INCREMENTAL (git-aware)"));
        Log.info("=".repeat(80));

        GpxPolylineService polylineService = new GpxPolylineService();
        Set<String> modifiedGpxFiles = Set.of();

        if (!fullScan) {
            // Try to get modified files from git
            try (Git git = Git.open(gitRepo.toFile())) {
                Repository repository = git.getRepository();
                modifiedGpxFiles = GitHelper.getModifiedGpxList(git, repository);
                Log.info("Found %d modified GPX file(s) in git".formatted(modifiedGpxFiles.size()));
            } catch (Exception e) {
                Log.warn("Could not read git repository (falling back to full scan): %s".formatted(e.getMessage()));
                fullScan = true;
            }
        }

        var gpxToMapWalker = new GitAwareGpxToMapWalker(modifiedGpxFiles, polylineService, fullScan);
        Files.walkFileTree(contentPath, gpxToMapWalker);    
        Log.info("=".repeat(80));
        Log.info("Generation complete!");
        Log.info("=".repeat(80));

        return 0;
    }

    private void printHelp() {
        System.out.println("""
            
            Cyclist Diary Polyline Generator CLI
            =====================================
            
            Usage:
              java -jar app.jar <command> [options]
              
            OR with Quarkus dev mode:
              ./mvnw quarkus:dev -Dquarkus.args="<command> [options]"
            
            Commands:
              generate-polylines    Generate polyline JSON files from GPX files
            
            Options:
              --help, -h           Show this help message
            
            Examples:
              # Generate polylines for modified GPX files (git-aware)
              java -jar app.jar generate-polylines
              
              # Generate polylines for ALL GPX files
              java -jar app.jar generate-polylines --full-scan
              
              # Specify custom content path
              java -jar app.jar generate-polylines --path /path/to/content
              
              # Full scan with custom paths
              java -jar app.jar generate-polylines --full-scan --path ../my-blog/content
            
            For more help on a specific command:
              java -jar app.jar generate-polylines --help
            """);
    }

    private void printGenerateHelp() {
        System.out.println("""
            
            Generate Polylines Command
            ==========================
            
            Generate encoded polyline JSON files from GPX tracks.
            By default, only processes GPX files modified in the last git commit.
            
            Usage:
              generate-polylines [options]
            
            Options:
              --path, -p <path>        Path to content folder containing GPX files
                                       (default: ./content)
              
              --full-scan, -f          Process ALL GPX files, not just modified ones
                                       (useful for initial setup or bulk regeneration)
              
              --git-repo, -g <path>    Path to git repository root
                                       (default: current directory)
              
              --help, -h               Show this help message
            
            Output:
              For each GPX file processed, creates a corresponding .polyline.json file
              in the same directory with:
              - Encoded polyline path (Base64-encoded Google Polyline)
              - Encoded elevations (Base64-encoded Int16 array)
              - Track metadata (distance, elevation gain/loss, duration, speed)
            
            Examples:
              # Process modified GPX files in default location
              generate-polylines
              
              # Process ALL GPX files
              generate-polylines --full-scan
              
              # Process GPX files in custom location
              generate-polylines --path ../my-blog/content
              
              # Full scan in custom location
              generate-polylines --full-scan --path /var/www/blog/content
            """);
    }
}
