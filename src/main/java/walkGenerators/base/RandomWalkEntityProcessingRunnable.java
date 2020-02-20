package walkGenerators.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A single task for the thread pool.
 */
public class RandomWalkEntityProcessingRunnable implements Runnable {

    /**
     * Entity that is processed by this thread.
     */
    String entity;

    /**
     * The list containing the walks generated by teh thread.
     */
    List<String> finalList;

    /**
     * Length of each walk.
     */
    int walkLength;

    /**
     * Number of walks to be performed per entity.
     */
    int numberOfWalks;

    /**
     * The walk generator for which this parser works.
     */
    WalkGenerator walkGenerator;

    /**
     * Constructor.
     *
     * @param generator The walk generator to be used.
     * @param entity        The entity this particular thread shall handle.
     * @param numberOfWalks The number of walks to be performed per entity.
     * @param walkLength The length of the walk.
     */
    public RandomWalkEntityProcessingRunnable(WalkGenerator generator, String entity, int numberOfWalks, int walkLength) {
        this.entity = entity;
        this.numberOfWalks = numberOfWalks;
        this.walkLength = walkLength;
        this.walkGenerator = generator;
        finalList = new ArrayList<>();
    }

    /**
     * Actual thread execution.
     */
    public void run() {
        processEntity();
        walkGenerator.writeToFile(finalList);
    }


    /**
     * This method generates the random walks for each entity.
     */
    private void processEntity() {
        int currentDepth;
        String currentWalk;
        int currentWalkNumber = 0;
        String entityShort = walkGenerator.shortenUri(entity);

        nextWalk:
        while (currentWalkNumber < numberOfWalks) {
            currentWalkNumber++;
            String lastObject = entity;
            currentWalk = entityShort;
            currentDepth = 0;
            while (currentDepth < walkLength) {
                currentDepth++;
                PredicateObject po = ((NtMemoryParser)walkGenerator.parser).getRandomPredicateObjectForSubjectWithoutTags(lastObject);
                if(po != null){
                    currentWalk += " " + walkGenerator.shortenUri(po.predicate) + " " + walkGenerator.shortenUri(po.object);
                    lastObject = po.object;
                } else {
                    // The current walk cannot be continued -> add to list (if there is a walk of depth 1) and create next walk.
                    if(currentWalk.length() != entityShort.length()) finalList.add(currentWalk);
                    continue nextWalk;
                }
            }
            finalList.add(currentWalk);
        }
    } // end of processEntity()

}
