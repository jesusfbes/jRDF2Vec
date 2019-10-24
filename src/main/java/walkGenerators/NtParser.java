package walkGenerators;

import org.apache.jena.ontology.OntModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.IsearchCondition;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * A parser for NT files. Mainly implemented to support {@link NtParser#getRandomPredicateObjectForSubject(String)} in
 * an efficient way.
 */
public class NtParser {

    /**
     * the actual data structure
     */
    private HashMap<String, ArrayList<PredicateObject>> data;

    /**
     * Default logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(NtParser.class);

    /**
     * Walk generator that uses the parser.
     */
    private WalkGenerator specificWalkGenerator;


    /**
     * returns true if a triple shall be excluded.
     */
    private IsearchCondition skipCondition;


    /**
     * Default Constructor
     */
    public NtParser(WalkGenerator walkGenerator){
        data = new HashMap<>(10000000);
        specificWalkGenerator = walkGenerator;
        skipCondition = new IsearchCondition() {
            Pattern pattern = Pattern.compile("\".*\"");
            @Override
            public boolean isHit(String input) {
                if(input.trim().startsWith("#")) return true; // just a comment line
                if(input.trim().equals("")) return true; // empty line
                Matcher matcher = pattern.matcher(input);
                if(matcher.find()) return true;
                return false;
            }
        };
    }


    /**
     * Constructor
     * @param pathToTripleFile The nt file to be read (not zipped).
     */
    public NtParser(String pathToTripleFile, WalkGenerator walkGenerator){
        this(walkGenerator);
        readNTriplesFileWithoutDataTypeValues(pathToTripleFile);
    }


    /**
     * Save an ontModel as TTL file.
     * @param ontModel Model to Write.
     * @param filePathToFileToWrite File that shall be written.
     */
    public static void saveAsNt(OntModel ontModel, String filePathToFileToWrite){
        try {
            ontModel.write(new FileWriter(new File(filePathToFileToWrite)), "N-Triples");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Read the given nt file into memory.
     * @param pathToFile Path to the file.
     */
    public void readNTriplesFileWithoutDataTypeValues(String pathToFile){
        readNTriplesFileWithoutDataTypeValues(pathToFile, false);
    }


    /**
     * Will load all .nt and .gz files from the given directory.
     * @param pathToDirectory The directory where the n-triple files reside.
     */
    public void readNTriplesFilesFromDirectory(String pathToDirectory) {
        File directoryOfDataSets = new File(pathToDirectory);
        if (!directoryOfDataSets.isDirectory()) {
            LOGGER.error("The given pathToDirectory is no directory, aborting. (given: " + pathToDirectory + ")");
            return;
        }
        for (File file : directoryOfDataSets.listFiles()) {
            LOGGER.info("Processing file " + file.getName());
            if (file.getName().endsWith(".gz")) {
                readNTriplesFileWithoutDataTypeValues(file, true);
            } else if(file.getName().endsWith(".nt")){
                readNTriplesFileWithoutDataTypeValues(file, false);
            } else continue;
        }
    }


    /**
     * Read the given nt file into memory. This method will add the data in the file to the existing {@link NtParser#data} store.
     * @param pathToFile Path to the file.
     * @param isGzippedFile Indicator whether the given file is gzipped.
     */
    public void readNTriplesFileWithoutDataTypeValues(String pathToFile, boolean isGzippedFile){
        File fileToReadFrom = new File(pathToFile);
        readNTriplesFileWithoutDataTypeValues(fileToReadFrom, isGzippedFile);
    }


    /**
     * Read the given nt file into memory. This method will add the data in the file to the existing {@link NtParser#data} store.
     * @param fileToReadFrom the file.
     * @param isGzippedFile Indicator whether the given file is gzipped.
     */
    public void readNTriplesFileWithoutDataTypeValues(File fileToReadFrom, boolean isGzippedFile){
        if(!fileToReadFrom.exists()){
            LOGGER.error("File does not exist. Cannot parse.");
            return;
        }
        try {
            BufferedReader reader = null;
            if(isGzippedFile){
                GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(fileToReadFrom));
                reader = new BufferedReader(new InputStreamReader(gzip));
            } else {
                reader = new BufferedReader(new FileReader(fileToReadFrom));
            }
            String readLine;
            long lineNumber = 0;
            nextLine:
            while((readLine = reader.readLine()) != null){
                lineNumber++;
                if(skipCondition.isHit(readLine)){
                    continue nextLine;
                }
                readLine = readLine.replaceAll("(?<=>)*[ ]*.[ ]*$", "");
                String[] spo = readLine.split(" ");
                if(spo.length != 3){
                    LOGGER.error("Error in file " + fileToReadFrom.getName() + " in line " + lineNumber + " while parsing the following line:\n" + readLine + "\n Required tokens: 3\nActual tokens: " + spo.length);
                    int i = 1;
                    for(String token : spo){
                        LOGGER.error("Token " + i++ + ": " + token);
                    }
                    continue nextLine;
                }
                String subject = specificWalkGenerator.shortenUri(removeTags(spo[0]));
                subject = subject.intern();
                if(data.get(subject) == null){
                    ArrayList<PredicateObject> list = new ArrayList<>();
                    list.add(new PredicateObject(specificWalkGenerator.shortenUri(removeTags(spo[1]).intern()), specificWalkGenerator.shortenUri(removeTags(spo[2])).intern()));
                    data.put(subject, list);
                } else {
                    ArrayList<PredicateObject> list = data.get(subject);
                    list.add(new PredicateObject(specificWalkGenerator.shortenUri(removeTags(spo[1]).intern()), specificWalkGenerator.shortenUri(removeTags(spo[2])).intern()));
                }
            } // end of while loop
            LOGGER.info("File successfuly read. " + data.size() + " subjects loaded.");
        } catch (Exception e) { LOGGER.error("Error while parsing file.", e); }
    }


    /**
     * Obtain a predicate and object for the given subject.
     * @param subject The subject for which a random predicate and object shall be found.
     * @return Predicate and object, randomly obtained for the given subject.
     */
    public PredicateObject getRandomPredicateObjectForSubject(String subject){
        if(subject == null) return null;
        subject = specificWalkGenerator.shortenUri(removeTags(subject));
        ArrayList<PredicateObject> queryResult = data.get(subject);
        if(queryResult == null){
            // no triple found
            return null;
        }
        int randomNumber = ThreadLocalRandom.current().nextInt(queryResult.size());
        LOGGER.info("(" + Thread.currentThread().getName() + ") " + randomNumber);
        return queryResult.get(randomNumber);
    }


    /**
     * Generates duplication free walks for the given entitiy.
     * @param entity The entity for which walks shall be generated.
     * @param numberOfWalks The number of walks to be generated.
     * @param depth The number of hops to nodes (!).
     * @return A list of walks.
     */
    public List<String> generateWalksForEntity(String entity, int numberOfWalks, int depth){
        List<String> result = new ArrayList<>();
        List<List<PredicateObject>> walks = new ArrayList();
        boolean isFirstIteration = true;
        for(int currentDepth = 0; currentDepth < depth; currentDepth++){
            // initialize with first node
            if(isFirstIteration) {
                ArrayList<PredicateObject> neighbours = data.get(entity);
                if(neighbours == null || neighbours.size() == 0){
                    return result;
                }
                for(PredicateObject neighbour : neighbours){
                    ArrayList<PredicateObject> individualWalk = new ArrayList<>();
                    individualWalk.add(neighbour);
                    walks.add(individualWalk);
                }
                isFirstIteration = false;
            }

            // create a copy
            List<List<PredicateObject>> walks_tmp = new ArrayList<>();
            walks_tmp.addAll(walks);

            // loop over current walks
            for(List<PredicateObject> walk : walks_tmp){
                // get last entity
                PredicateObject lastPredicateObject = walk.get(walk.size()-1);
                ArrayList<PredicateObject> nextIteration = data.get(lastPredicateObject.object);
                if(nextIteration != null){
                    walks.remove(walk); // check whether this works
                    for(PredicateObject nextStep : nextIteration) {
                        List<PredicateObject> newWalk = new ArrayList<>(walk);
                        newWalk.add(nextStep);
                        walks.add(newWalk);
                    }
                }
            } // loop over walks

            // trim the list
            while(walks.size() > numberOfWalks){
                int randomNumber = ThreadLocalRandom.current().nextInt(walks.size());
                walks.remove(randomNumber);
            }
        } // depth loop

        // now we need to translate our walks into strings
        for(List<PredicateObject> walk : walks){
            String finalSentence = entity;
            for(PredicateObject po : walk){
                finalSentence += " " +  po.predicate + " " + po.object;
            }
            result.add(finalSentence);
        }
        return result;
    }


    /**
     * Faster version of {@link NtParser#getRandomPredicateObjectForSubject(String)}.
     * Note that there cannot be any leading less-than or trailing greater-than signs around the subject.
     * The subject URI should already be shortened.
     * @param subject The subject for which a random predicate and object shall be found.
     * @return Predicate and object, randomly obtained for the given subject.
     */
    public PredicateObject getRandomPredicateObjectForSubjectWithoutTags(String subject){
        if(subject == null) return null;
        ArrayList<PredicateObject> queryResult = data.get(subject);
        if(queryResult == null){
            // no triple found
            return null;
        }
        int randomNumber = ThreadLocalRandom.current().nextInt(queryResult.size());
        //System.out.println("(" + Thread.currentThread().getName() + ") " + randomNumber + "[" + queryResult.size() + "]");
        return queryResult.get(randomNumber);
    }

    public WalkGenerator getSpecificWalkGenerator() {
        return specificWalkGenerator;
    }

    public void setSpecificWalkGenerator(WalkGenerator specificWalkGenerator) {
        this.specificWalkGenerator = specificWalkGenerator;
    }

    public IsearchCondition getSkipCondition() {
        return skipCondition;
    }

    public void setSkipCondition(IsearchCondition skipCondition) {
        this.skipCondition = skipCondition;
    }

    /**
     * This method will remove a leading less-than and a trailing greater-than sign (tags).
     * @param stringToBeEdited The string that is to be edited.
     * @return String without tags.
     */
    static String removeTags(String stringToBeEdited){
        if(stringToBeEdited.startsWith("<")) stringToBeEdited = stringToBeEdited.substring(1);
        if(stringToBeEdited.endsWith(">")) stringToBeEdited = stringToBeEdited.substring(0, stringToBeEdited.length() - 1);
        return stringToBeEdited;
    }
}
