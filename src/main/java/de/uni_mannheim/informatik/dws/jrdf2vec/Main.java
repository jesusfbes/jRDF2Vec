package de.uni_mannheim.informatik.dws.jrdf2vec;

import de.uni_mannheim.informatik.dws.jrdf2vec.training.Gensim;
import de.uni_mannheim.informatik.dws.jrdf2vec.training.Word2VecConfiguration;
import de.uni_mannheim.informatik.dws.jrdf2vec.training.Word2VecType;
import de.uni_mannheim.informatik.dws.jrdf2vec.util.Util;
import de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.base.WalkGenerationMode;
import de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.base.WalkGeneratorDefault;
import de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.light.WalkGeneratorLight;

import java.io.File;
import java.time.Instant;

/**
 * Mini command line tool for server application.
 */
public class Main {

    /**
     * word2vec configuration (not just CBOW/SG but contains also all other parameters)
     */
    private static Word2VecConfiguration configuration = new Word2VecConfiguration(Word2VecType.SG);

    /**
     * File for light-weight generation
     */
    private static File lightEntityFile = null;

    /**
     * File to the knowledge graph
     */
    private static File knowledgeGraphFile = null;

    /**
     * The number of threads to be used for the walk generation and for the training.
     */
    private static int numberOfThreads = -1;

    /**
     * Dimensions for the vectors.
     */
    private static int dimensions = -1;

    /**
     * Word2vec mincount parameter.
     */
    private static int minCount = Word2VecConfiguration.MIN_COUNT_DEFAULT;

    /**
     * Default value to be used for the depth.
     */
    public static final int DEFAULT_DEPTH = 4;

    /**
     * Depth for the walks to be generated.
     */
    private static int depth = DEFAULT_DEPTH;

    /**
     * The default number of walks to be generated per node in the graph.
     */
    public static final int DEFAULT_NUMBER_OF_WALKS = 100;

    /**
     * The number of walks to be generated for each node.
     * Default: 100
     */
    private static int numberOfWalks = DEFAULT_NUMBER_OF_WALKS;

    /**
     * The file to which the python resources shall be copied.
     */
    private static File resourcesDirectory;

    /**
     * Orchestration instance
     */
    private static IRDF2Vec rdf2VecInstance;

    /**
     * Where the walks will be persisted (directory).
     */
    private static File walkDirectory = null;

    /**
     * Walk generation mode.
     */
    private static WalkGenerationMode walkGenerationMode = null;

    /**
     * If true, only walks are generated and no embeddings are trained.
     * This can be beneficial when multiple configurations (e.g. SG and CBOW) shall be trained for only one set of walks.
     */
    private static boolean isOnlyWalks = false;

    /**
     * If true, only the training step is executed.
     */
    private static boolean isOnlyTraining = false;

    /**
     * By default a vector text file is generated.
     */
    private static boolean isVectorTextFileGeneration = true;


    public static void main(String[] args) {

        if (args == null || args.length == 0) {
            System.out.println("Not enough arguments. Call '-help' to learn more about the CLI.");
            return;
        }

        if (containsIgnoreCase("-help", args) || containsIgnoreCase("--help", args) || containsIgnoreCase("-h", args)) {
            System.out.println(getHelp());
            return;
        }

        if(args.length == 2) {
            String transformationSource = getValue("-generateTxtVectorFile", args);
            if(transformationSource == null) {
                // check for alternative spelling
                transformationSource = getValue("-generateTextVectorFile", args);
            }
            if(transformationSource != null) {
                generateTextVectorFile(transformationSource);
                return;
            }
        }

        if(containsIgnoreCase("-onlyTraining", args)){
            isOnlyTraining = true;
            String walksPath = getValue("-walkDirectory", args);
            if(walksPath == null){
                // try again with a different writing
                walksPath = getValue("-walkDir",args);
                if(walksPath == null) {
                    System.out.println("Required parameter -walkDirectory <path to walk directory or file> missing. Aborting program. Call '-help' to learn more about the CLI.");
                    return;
                }
            }
        }

        String knowledgeGraphFilePath = getValue("-graph", args);

        isOnlyWalks = containsIgnoreCase("-onlyWalks", args);
        // allowing a bit more...
        if (!isOnlyWalks) isOnlyWalks = containsIgnoreCase("-walksOnly", args);

        if(!isOnlyTraining) {
            // the KG file path is only relevant if we want to do walk generation...
            if (knowledgeGraphFilePath == null) {
                System.out.println("Required parameter '-graph <kg_file>' not set - program cannot be started. " +
                        "Call '-help' to learn more about the CLI.");
                // stop program execution
                return;
            }
            knowledgeGraphFile = new File(knowledgeGraphFilePath);
            if (!knowledgeGraphFile.exists()) {
                System.out.println("The given file does not exist: " + knowledgeGraphFilePath);
                // stop program execution
                return;
            }
        }

        String lightEntityFilePath = getValue("-light", args);
        if (lightEntityFilePath != null) {
            lightEntityFile = new File(lightEntityFilePath);
            if (!lightEntityFile.exists()) {
                System.out.println("The given file does not exist: " + lightEntityFilePath);
            }
        }



        String walkDirectoryPath = getValue("-walkDir", args);
        walkDirectoryPath = (walkDirectoryPath == null) ? getValue("-walkDirectory", args) : walkDirectoryPath;
        if (walkDirectoryPath != null) {
            walkDirectory = new File(walkDirectoryPath);
            if (!walkDirectory.isDirectory()) {
                System.out.println("Walk directory is no directory! Using default.");
                walkDirectory = null;
            }
        }

        String threadsText = getValue("-threads", args);
        if (threadsText != null) {
            try {
                numberOfThreads = Integer.parseInt(threadsText);
            } catch (NumberFormatException nfe) {
                System.out.println("Could not parse the number of threads. Using default.");
                numberOfThreads = Runtime.getRuntime().availableProcessors() / 2;
            }
        } else numberOfThreads = Runtime.getRuntime().availableProcessors() / 2;
        System.out.println("Using " + numberOfThreads + " threads for walk generation and training.");

        String dimensionText = getValue("-dimension", args);
        dimensionText = (dimensionText == null) ? getValue("-dimensions", args) : dimensionText;
        if (dimensionText != null) {
            try {
                dimensions = Integer.parseInt(dimensionText);
            } catch (NumberFormatException nfe) {
                System.out.println("Could not parse the number of dimensions. Using default (" + Word2VecConfiguration.VECTOR_DIMENSION_DEFAULT + ").");
                dimensions = Word2VecConfiguration.VECTOR_DIMENSION_DEFAULT;
            }
        } else dimensions = Word2VecConfiguration.VECTOR_DIMENSION_DEFAULT;
        if (!isOnlyWalks) System.out.println("Using vector dimension: " + dimensions);

        String depthText = getValue("-depth", args);
        if (depthText != null) {
            try {
                depth = Integer.parseInt(depthText);
            } catch (NumberFormatException nfe) {
                System.out.println("Could not parse the depth. Using default (" + DEFAULT_DEPTH + ").");
                depth = DEFAULT_DEPTH;
            }
        } else depth = DEFAULT_DEPTH;
        System.out.println("Using depth " + depth);

        String numberOfWalksText = getValue("-numberOfWalks", args);
        numberOfWalksText = (numberOfWalksText == null) ? getValue("-numOfWalks", args) : numberOfWalksText;
        numberOfWalksText = (numberOfWalksText == null) ? getValue("-numOfWalks", args) : numberOfWalksText;
        if (numberOfWalksText != null) {
            try {
                numberOfWalks = Integer.parseInt(numberOfWalksText);
            } catch (NumberFormatException nfe) {
                System.out.println("Could not parse the number of walks. Using default.");
            }
        }
        System.out.println("Generating " + numberOfWalks + " walks per entity.");

        String resourcesDirectroyPath = getValue("-serverResourcesDir", args);
        if (resourcesDirectroyPath != null) {
            File f = new File(resourcesDirectroyPath);
            if (f.isDirectory()) {
                resourcesDirectory = f;
            } else {
                System.out.println("The specified directory for the python resources is not a directory. Using default.");
            }
        }

        String minCountString = getValue("-minCount", args);
        if (minCountString != null) {
            try {
                minCount = Integer.parseInt(minCountString);
            } catch (NumberFormatException nfe) {
                System.out.println("Could not parse the minCount. Using default (" + Word2VecConfiguration.MIN_COUNT_DEFAULT + ").");
                minCount = Word2VecConfiguration.MIN_COUNT_DEFAULT;
            }
        } else minCount = Word2VecConfiguration.MIN_COUNT_DEFAULT;


        if(containsIgnoreCase("-noVectorTextFileGeneration", args)){
            isVectorTextFileGeneration = false;
        } else if(containsIgnoreCase("-vectorTextFileGeneration", args)){
            isVectorTextFileGeneration = true;
        }

        // determining the configuration for the training
        String trainingModeText = getValue("-trainingMode", args);
        trainingModeText = (trainingModeText == null) ? getValue("-trainMode", args) : trainingModeText;
        if (trainingModeText != null) {
            if (trainingModeText.equalsIgnoreCase("sg")) {
                configuration = new Word2VecConfiguration(Word2VecType.SG);
            } else configuration = new Word2VecConfiguration(Word2VecType.CBOW);
        } else configuration = new Word2VecConfiguration(Word2VecType.SG); // default: SG

        // setting training threads
        if (numberOfThreads > 0) configuration.setNumberOfThreads(numberOfThreads);

        // setting dimensions
        if (dimensions > 0) configuration.setVectorDimension(dimensions);

        // setting minCount
        if (minCount > 0) configuration.setMinCount(minCount);

        String walkGenerationModeText = getValue("-walkGenerationMode", args);
        walkGenerationModeText = (walkGenerationModeText == null) ? getValue("-walkMode", args) : walkGenerationModeText;
        if (walkGenerationModeText != null) {
            walkGenerationMode = WalkGenerationMode.getModeFromString(walkGenerationModeText);
        }

        Instant before, after;

        // -------------------
        //    only training
        // -------------------
        if(isOnlyTraining){
            System.out.println("Only training is performed, no walks are going to be generated.");
            before = Instant.now();
            String modelFilePathToWrite = walkDirectory.getAbsolutePath() + "/model.kv";
            Gensim.getInstance().trainWord2VecModel(modelFilePathToWrite, walkDirectory.getAbsolutePath(), configuration);
            Gensim.getInstance().writeModelAsTextFile(modelFilePathToWrite, walkDirectory.getAbsolutePath() + "/vectors.txt");
            after = Instant.now();
            System.out.println("\nTotal Time:");
            System.out.println(Util.getDeltaTimeString(before, after));
            return;
        }


        // ------------------
        //     only walks
        // ------------------

        if (isOnlyWalks) {
            System.out.println("Only walks are being generated, training is performed.");
            String walkFile = "./walks/walk_file.gz";

            // handle the walk directory
            if (walkDirectory == null || !walkDirectory.isDirectory()) {
                System.out.println("walkDirectory is not a directory. Using default: " + walkFile);
            } else {
                walkFile = walkDirectory.getAbsolutePath() + "/walk_file.gz";
            }

            before = Instant.now();

            // now distinguish light/non-light
            if (lightEntityFile != null) {
                // light walk generation:
                WalkGeneratorLight generatorLight = new WalkGeneratorLight(knowledgeGraphFile, lightEntityFile);
                walkGenerationMode = (walkGenerationMode == null) ? WalkGenerationMode.MID_WALKS : walkGenerationMode;
                generatorLight.generateWalks(walkGenerationMode, numberOfThreads, numberOfWalks, depth, walkFile);

            } else {
                // classic walk generation
                WalkGeneratorDefault classicGenerator = new WalkGeneratorDefault(knowledgeGraphFile);
                walkGenerationMode = (walkGenerationMode == null) ? WalkGenerationMode.RANDOM_WALKS_DUPLICATE_FREE : walkGenerationMode;
                classicGenerator.generateWalks(walkGenerationMode, numberOfThreads, numberOfWalks, depth, walkFile);
            }

            after = Instant.now();

            System.out.println("\nTotal Time:");
            System.out.println(Util.getDeltaTimeString(before, after));

            return; // important: stop here to avoid any training.
        }


        // ------------------------------------
        //     full run (walks + training)
        // ------------------------------------

        if (lightEntityFile == null) {
            System.out.println("RDF2Vec Classic");

            RDF2Vec rdf2vec;
            if (walkDirectory == null) rdf2vec = new RDF2Vec(knowledgeGraphFile);
            else rdf2vec = new RDF2Vec(knowledgeGraphFile, walkDirectory);

            // setting threads
            if (numberOfThreads > 0) rdf2vec.setNumberOfThreads(numberOfThreads);

            // setting depth
            if (depth > 0) rdf2vec.setDepth(depth);

            // setting the number of walks
            if (numberOfWalks > 0) rdf2vec.setNumberOfWalksPerEntity(numberOfWalks);

            // setting the walk generation mode
            rdf2vec.setWalkGenerationMode(walkGenerationMode);

            // set resource directory for python server files
            if (resourcesDirectory != null) rdf2vec.setPythonServerResourceDirectory(resourcesDirectory);

            // setting the walk generation mode
            rdf2vec.setWalkGenerationMode(walkGenerationMode);

            // set vector text file
            rdf2vec.setVectorTextFileGeneration(isVectorTextFileGeneration);

            rdf2vec.setConfiguration(configuration);
            before = Instant.now();
            rdf2vec.train();
            after = Instant.now();

            // setting the instance to allow for better testability
            rdf2VecInstance = rdf2vec;
        } else {
            System.out.println("RDF2Vec Light Mode");
            RDF2VecLight rdf2VecLight;
            if (walkDirectory == null) rdf2VecLight = new RDF2VecLight(knowledgeGraphFile, lightEntityFile);
            else rdf2VecLight = new RDF2VecLight(knowledgeGraphFile, lightEntityFile, walkDirectory);

            // setting threads
            if (numberOfThreads > 0) rdf2VecLight.setNumberOfThreads(numberOfThreads);

            // setting depth
            if (depth > 0) rdf2VecLight.setDepth(depth);

            // setting the number of walks
            if (numberOfWalks > 0) rdf2VecLight.setNumberOfWalksPerEntity(numberOfWalks);

            // set resource directory
            if (resourcesDirectory != null) rdf2VecLight.setResourceDirectory(resourcesDirectory);

            // set vector text file
            rdf2VecLight.setVectorTextFileGeneration(isVectorTextFileGeneration);

            // setting the walk generation mode
            rdf2VecLight.setWalkGenerationMode(walkGenerationMode);

            rdf2VecLight.setConfiguration(configuration);
            before = Instant.now();
            rdf2VecLight.train();
            after = Instant.now();

            // setting the instance to allow for better testability
            rdf2VecInstance = rdf2VecLight;
        }

        System.out.println("\nTotal Time:");
        System.out.println(Util.getDeltaTimeString(before, after));

        System.out.println("\nWalk Generation Time:");
        System.out.println(rdf2VecInstance.getRequiredTimeForLastWalkGenerationString());

        System.out.println("\nTraining Time:");
        System.out.println(rdf2VecInstance.getRequiredTimeForLastTrainingString());
    }

    /**
     * Given a model or vector file, a text file is generated containing all the vectors.
     * @param transformationSource File path to the model or vector file.
     */
    private static void generateTextVectorFile(String transformationSource) {
        File sourceFile = new File(transformationSource);
        if(!sourceFile.exists()){
            System.out.println("The given file does not exist. Cannot generate text vector file.");
            return;
        }
        if(sourceFile.isDirectory()){
            System.out.println("The specified file is a directory. Cannot generate text vector file.");
            return;
        }
        File fileToGenerate = new File(sourceFile.getParentFile().getAbsolutePath(), "vectors.txt");
        Gensim.getInstance().writeModelAsTextFile(transformationSource, fileToGenerate.getAbsolutePath());
    }


    /**
     * Helper method.
     *
     * @param key       Arg key.
     * @param arguments Arguments as received upon program start.
     * @return Value of argument if existing, else null.
     */
    public static String getValue(String key, String[] arguments) {
        if (arguments == null) return null;
        int positionSet = -1;
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].equalsIgnoreCase(key)) {
                positionSet = i;
                break;
            }
        }
        if (positionSet != -1 && arguments.length >= positionSet + 1) {
            return arguments[positionSet + 1];
        } else return null;
    }

    /**
     * Check whether {@code element} is contained in {@code array}.
     *
     * @param element The element that shall be looked for.
     * @param array   The array in which shall be looked for the element.
     * @return True if {@code element} is contained in {@code array}, else false.
     */
    public static boolean containsIgnoreCase(String element, String[] array) {
        if (element == null || array == null) return false;
        for (String s : array) {
            if (element.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    /**
     * Get the instance for testing. Not required for operational usage.
     *
     * @return RDF2Vec instance.
     */
    public static IRDF2Vec getRdf2VecInstance() {
        return rdf2VecInstance;
    }

    /**
     * Get the walk generation mode for testing. Not required for operational usage.
     *
     * @return Walk Generation Mode.
     */
    public static WalkGenerationMode getWalkGenerationMode() {
        return walkGenerationMode;
    }

    /**
     * Get depth for testing. Not required for operational usage.
     *
     * @return Depth as int.
     */
    public static int getDepth() {
        return depth;
    }

    /**
     * Get the help text on how to use the CLI.
     * Developer note: Also add new commands to the README.
     *
     * @return Help text as String.
     */
    public static String getHelp() {

        return  "*****************\n" +
                "* jRDF2Vec Help *\n" +
                "*****************\n\n" +

                "Walk Generation and RDF2Vec Training\n" +
                "------------------------------------\n\n" +

                "Required Parameters:\n\n" +
                "    -graph <graph_file>\n" +
                "    The file containing the knowledge graph for which you want to generate embeddings.\n\n" +
                "Optional Parameters:\n\n" +
                "    -light <entity_file>\n" +
                "    If you intend to use RDF2Vec Light, you have to use this switch followed by the file path ot the describing the entities for which you require an embedding space. The file should contain one entity (full URI) per line.\n\n" +
                "    -onlyWalks\n" +
                "    If added to the call, this switch will deactivate the training part so that only walks are generated. If training parameters are specified, they are ignored. The walk generation also works with the `-light` parameter.\n\n" +
                "    -threads <number_of_threads> (default: (# of available processors) / 2)\n" +
                "    This parameter allows you to set the number of threads that shall be used for the walk generation as well as for the training.\n\n" +
                "    -dimension <size_of_vector> (default: 200)\n" +
                "    This parameter allows you to control the size of the resulting vectors (e.g. 100 for 100-dimensional vectors).\n\n" +
                "    -depth <depth> (default: 4)\n" +
                "    This parameter controls the depth of each walk. Depth is defined as the number of hops. Hence, you can also set an odd number. A depth of 1 leads to a sentence in the form <s p o>.\n\n" +
                "    -trainingMode <cbow|sg> (default: sg)\n" +
                "    This parameter controls the mode to be used for the word2vec training. Allowed values are cbow and sg.\n\n" +
                "    -numberOfWalks <number> (default: 100)\n" +
                "    The number of walks to be performed per entity.\n\n" +
                "    -minCount <number> (default: 1)\n" +
                "    The minimum word count for the training. Unlike in the gensim defaults, this parameter is set to 1 because for KG embeddings, a vector for each node/arc is desired.\n\n" +
                "    -noVectorTextFileGeneration | -vectorTextFileGeneration\n" +
                "    A switch that indicates whether a text file with the vectors shall be persisted on the disk. This is enabled by default. Use -noVectorTextFileGeneration to disable the file generation.\n\n" +
                "    -walkGenerationMode <MID_WALKS | MID_WALKS_DUPLICATE_FREE | RANDOM_WALKS | RANDOM_WALKS_DUPLICATE_FREE> (default for light: MID_WALKS, default for classic: RANDOM_WALKS_DUPLICATE_FREE)\n" +
                "    This parameter determines the mode for the walk generation (multiple walk generation algorithms are available). Reasonable defaults are set.\n\n" +
                "    -walkDirectory <directory where walk files shall be generated/reside>\n" +
                "    The directory where the walks shall be generated into. In case of -onlyTraining, the directory where the walks reside.\n\n" +
                "    -onlyTraining\n" +
                "    If added to the call, this switch will deactivate the walk generation part so that only the training is performed. The parameter -walkDirectory must be set. If walk generation parameters are specified, they are ignored.\n\n\n" +


                "Additional Services\n" +
                "-------------------\n\n" +

                "A) Generation of Text Vector File\n" +
                "   jRDF is compatible with the evaluation framework for KG embeddings (GEval). This framework requires the vectors to be present in a text file. If you have a gensim model or vector file, you can use the following parameter to generate this file:\n\n" +
                "       -generateTextVectorFile <model_or_vector_file>\n" +
                "        The file path to the model or vector file that shall be used to write the vectors in a text file needs to be specified.";
    }

    /**
     * Reset parameters (required for testing)
     */
    public static void reset() {
        configuration = new Word2VecConfiguration(Word2VecType.SG);
        lightEntityFile = null;
        knowledgeGraphFile = null;
        numberOfThreads = -1;
        dimensions = -1;
        depth = DEFAULT_DEPTH;
        numberOfWalks = DEFAULT_NUMBER_OF_WALKS;
        resourcesDirectory = null;
        rdf2VecInstance = null;
        walkGenerationMode = null;
        isVectorTextFileGeneration = true;
        isOnlyTraining = false;
    }

}
