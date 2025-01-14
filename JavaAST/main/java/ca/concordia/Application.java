package ca.concordia;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;


import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;

import java.lang.reflect.Type;

import static ca.concordia.FileUtil.findFile;
import static java.lang.Math.floor;


public class Application {

    static String data_file_path = "../data/merged_data_production_bug_reports.json";


    static Map<String, String> projectsList = new HashMap<>() {{
        put("Lang", "/Users/lorenapacheco/Concordia/Masters/defects4j/project_repos/commons-lang.git/");
        put("Math", "/Users/lorenapacheco/Concordia/Masters/defects4j/project_repos/commons-math.git/");
        put("Cli", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-cli/");
        put("Closure", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/closure-compiler/");
        put("Codec", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-codec/");
        put("Collections", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-collections/");
        put("Compress", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-compress/");
        put("Csv", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-csv/");
        put("Gson", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/gson/");
        put("JacksonCore", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/jackson-core/");
        put("JacksonDatabind", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/jackson-databind/");
        put("Jsoup", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/jsoup/");
        put("JxPath", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/commons-jxpath/");
        put("Mockito", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/mockito/");
        put("Time", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/joda-time/");
        put("fastjson", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/fastjson/");
        put("junit4", "/Users/lorenapacheco/Concordia/Masters/open_source_repos_being_studied/junit4/");
    }};


    public static void main(String[] args){

        Gson gson = new GsonBuilder().create();




        try (FileReader reader = new FileReader(data_file_path)) {
            // Convert JSON to Java object
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            Map<String, Map<String, Object>> data = gson.fromJson(reader, type);

            // Access data from the object
            for (String project : data.keySet()) {
                if (project.equals("Lang") || project.equals("Math")){ //Skipping them for now since I can not run git checkout
                    continue;
                }
                System.out.println(project);
                String path_to_repo = projectsList.get(project);
                Map<String, Object> bugs = data.get(project);
                for (String bugID : bugs.keySet()) {
                    System.out.println(bugID);

                    Map<String, Object> bug = (Map<String, Object>) bugs.get(bugID);
                    String buggyCommit = (String) bug.get("buggy_commit");
                    String bugfixCommit = (String) bug.get("bugfix_commit");

                    // Checking the code
                    List<MethodData> addedLinesMethods;
                    List<MethodData> deletedLinesMethods;

                    Map<String,Map<String, Map<String, Integer>>> buggyMethods = new HashMap<>();
                    Map<String,Map<String, Map<String, Integer>>> newMethods = new HashMap<>();
                    Map<String, Map<String, Object>> modifiedCode = (Map<String, Map<String, Object>>) bug.get("modified_code");
                    for (String filePath : modifiedCode.keySet()) {
                        String filePathFixed = filePath;
                        if (filePath.startsWith("b/")) {
                            filePathFixed = filePath.substring(2);
                        }
                        String absolute_file_path = path_to_repo + filePath;
                        Map<String, Object> fileInfo = modifiedCode.get(filePath);
                        List<Double> deletedLinesDouble = (List<Double>) fileInfo.get("deleted_lines");
                        deletedLinesMethods = get_touched_methods(buggyCommit, path_to_repo,deletedLinesDouble, absolute_file_path, filePathFixed, false);
                        List<Double> addedLinesDouble = (List<Double>) fileInfo.get("added_lines");
                        addedLinesMethods = get_touched_methods(bugfixCommit, path_to_repo,addedLinesDouble, absolute_file_path, filePathFixed, false);

                        for (MethodData md : deletedLinesMethods){
                            String fileName = filePathFixed;
                            String methodName = md.getMethodName();
                            buggyMethods= addNewMethod(buggyMethods, methodName, fileName, md);
                        }
                        List<Integer> addedLines = convertDoubleListIntoIntegers(addedLinesDouble);
                        // If a method was added in the bugfix commit, it is not a buggy method
                        GitHelper.checkoutCommit(path_to_repo, bugfixCommit);
                        for (MethodData md : addedLinesMethods){
                            String fileName = filePathFixed;
                            String methodName = md.getMethodName();
                            int aproxLineNumber = (int) floor((md.getEndLine() - md.getStartLine()) / 2);
                            GitHelper.checkoutCommit(path_to_repo, buggyCommit);
                            MethodData newMd = null;
                            try {
                                newMd = MethodFinderFromName.findMethodLines(absolute_file_path, aproxLineNumber, methodName);
                            } catch (NoSuchFileException exception){
                                // newMethod
                            }
                            GitHelper.checkoutCommit(path_to_repo, bugfixCommit);
                            if (newMd != null && !deletedLinesMethods.contains(newMd)){
                                boolean isNewMethod = CheckIfMethodWasCreatedFinder.checkIfMethodWasCreated(absolute_file_path, addedLines, methodName );
                                if (isNewMethod){
                                    newMethods= addNewMethod(newMethods, methodName, fileName, md);
                                } else{
                                    buggyMethods= addNewMethod(buggyMethods, methodName, fileName, newMd);
                                }
                            }
                        }
                    }

                    // Checking the tests
                    List<MethodData> addedLinesTests;
                    List<MethodData> deletedLinesTests;

                    Map<String,Map<String, Map<String, Integer>>> updatedTests = new HashMap<>();
                    Map<String,Map<String, Map<String, Integer>>> newTests = new HashMap<>();
                    Map<String, Map<String, Object>> modifiedTests = new HashMap<>();
                    if (bug.get("modified_tests") != null){
                        modifiedTests = (Map<String, Map<String, Object>>) bug.get("modified_tests");
                    }
                    for (String filePath : modifiedTests.keySet()) {
                        String filePathFixed = filePath;
                        if (filePath.startsWith("b/")) {
                            filePathFixed = filePath.substring(2);
                        }
                        String absolute_file_path = path_to_repo + filePath;
                        Map<String, Object> fileInfo = modifiedTests.get(filePath);
                        List<Double> deletedLinesDouble = (List<Double>) fileInfo.get("deleted_lines");
                        deletedLinesTests = get_touched_methods(buggyCommit, path_to_repo, deletedLinesDouble, absolute_file_path, filePathFixed, true);
                        List<Double> addedLinesDouble = (List<Double>) fileInfo.get("added_lines");
                        addedLinesTests = get_touched_methods(bugfixCommit, path_to_repo, addedLinesDouble, absolute_file_path, filePathFixed, true);

                        for (MethodData md : deletedLinesTests){
                            String fileName = filePathFixed;
                            String testName = md.getMethodName();
                            updatedTests= addNewMethod(updatedTests, testName, fileName, md);
                        }
                        List<Integer> addedLines = convertDoubleListIntoIntegers(addedLinesDouble);
                        // If a method was added in the bugfix commit, it is not a buggy method
                        GitHelper.checkoutCommit(path_to_repo, bugfixCommit);
                        for (MethodData md : addedLinesTests) {
                            if (!deletedLinesTests.contains(md)) {
                                String fileName = filePathFixed;
                                String testName = md.getMethodName();
                                boolean newTest = CheckIfMethodWasCreatedFinder.checkIfMethodWasCreated(absolute_file_path, addedLines, testName);
                                if (newTest) {
                                    newTests= addNewMethod(newTests, testName, fileName, md);
                                } else {
                                    updatedTests= addNewMethod(updatedTests, testName, fileName, md);
                                }
                            }
                        }
                    }
                    List<String> st_files = (List<String>) bug.get("stack_trace_files");
                    List<String> st_methods = (List<String>) bug.get("stack_trace_methods");
                    List<String> st_lines = (List<String>) bug.get("stack_trace_lines");

                    Map<String,Map<String, Map<String, Integer>>> st_methods_details = new HashMap<>();
                    for (int i = 0; i < st_files.size(); i++) {
                        String fileName = st_files.get(i);
                        String method = st_methods.get(i);
                        String[] parts = method.split("\\.");
                        String methodName = parts[parts.length - 1].split("\\$")[0];
                        String line = st_lines.get(i);
                        if (line == "-1"){
                            continue;
                        }
                        GitHelper.checkoutCommit(path_to_repo, buggyCommit);
                        String absolute_st_file_path = null;
                        try {
                            absolute_st_file_path = findFile(Paths.get(path_to_repo), fileName);
                        } catch (IOException | NullPointerException e) {
                            // Not an internal file
                        }
                        MethodData md = null;
                        if (absolute_st_file_path != null) {
                            try {
                                md = MethodFinderFromName.findMethodLines(absolute_st_file_path, Integer.parseInt(line), methodName);
                            } catch (NoSuchFileException exception) {
                                // newMethod
                            }
                        }
                        if (md!=null){
                            st_methods_details= addNewMethod(st_methods_details, methodName, absolute_st_file_path, md);
                        }
                    }

                    bug.put("buggyMethods", buggyMethods);
                    bug.put("newMethods", newMethods);
                    bug.put("updatedTests", updatedTests);
                    bug.put("newTests", newTests);
                    bug.put("stackTraceMethodsDetails", st_methods_details);
                }
            }
            Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
            String updatedJson = gsonPretty.toJson(data);

            // Write the updated JSON string to the file
            try (FileWriter writer = new FileWriter(data_file_path)) {
                writer.write(updatedJson);
                System.out.println("Json file data/merged_data_production_bug_reports.json update with the extract information: buggyMethods, newMethods, updatedTests and newTests");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static List<MethodData> get_touched_methods(String commit, String pathToRepo, List<Double> linesDouble, String absoluteFilePath, String filePath, boolean isTest) throws IOException {
        List<MethodData> touchedMethods = new ArrayList<>();
        GitHelper.checkoutCommit(pathToRepo, commit);
        List<Integer> lines = convertDoubleListIntoIntegers(linesDouble);
        if (linesDouble != null) {
            for (Integer line: lines){
                String methodName = MethodFinderFromLine.findMethodName(absoluteFilePath, line);
                MethodData methodData = MethodFinderFromLine.getmethodData();
                if (methodName != null && !touchedMethods.contains(filePath+ " - " +methodName)) {
                    touchedMethods.add(methodData);
                }
            }
        }
        return touchedMethods;
    }
    public static boolean contains(List<MethodData> methodDataList, MethodData target) {
        return methodDataList.contains(target);
    }

    public static List<Integer> convertDoubleListIntoIntegers(List<Double> linesDouble) {
        List<Integer> lines = new ArrayList<>();
        if (linesDouble != null) {
            for (Double d : linesDouble) {
                lines.add(d.intValue());
            }
        }
        return lines;
    }

    public static Map<String, Map<String, Map<String, Integer>>> addNewMethod(Map<String, Map<String, Map<String, Integer>>> methodsObject, String methodName, String fileName, MethodData methodData){
        if (!methodsObject.keySet().contains(fileName)) {
            methodsObject.put(fileName, new HashMap<>());
        }
        int startLine = methodData.getStartLine();
        int endLine = methodData.getEndLine();
        Map<String, Integer> methodInfo = new HashMap<>();
        methodInfo.put("startLine",startLine);
        methodInfo.put("endLine",endLine);
        Map<String, Map<String, Integer>> fileMethods = methodsObject.get(fileName);
        fileMethods.put(methodName, methodInfo);
        methodsObject.put(fileName, fileMethods);
        return methodsObject;
    }

}