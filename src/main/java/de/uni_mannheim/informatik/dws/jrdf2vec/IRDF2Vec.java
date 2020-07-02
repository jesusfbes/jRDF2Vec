package de.uni_mannheim.informatik.dws.jrdf2vec;

import de.uni_mannheim.informatik.dws.jrdf2vec.training.Word2VecConfiguration;
import de.uni_mannheim.informatik.dws.jrdf2vec.walkGenerators.base.WalkGenerationMode;

/**
 * Interface for de.uni_mannheim.informatik.dws.jrdf2vec.RDF2Vec orchestration classes.
 */
public interface IRDF2Vec {

    /**
     * This method returns the time it took to generate walks for the last run as String.
     * @return The time it took to generate walks for the last run as String. Will never be null.
     */
    String getRequiredTimeForLastWalkGenerationString();

    /**
     * This method returns he time it took to train the model for the last run as String.
     * @return The time it took to train the model for the last run as String. Will never be null.
     */
    String getRequiredTimeForLastTrainingString();

    /**
     * Set the walk generation mode.
     * @param walkGenerationMode Mode to use.
     */
    void setWalkGenerationMode(WalkGenerationMode walkGenerationMode);

    /**
     * Set the walk generation mode for the generation part of de.uni_mannheim.informatik.dws.jrdf2vec.RDF2Vec.
     * @return {@link WalkGenerationMode} to be used.
     */
    WalkGenerationMode getWalkGenerationMode();

    /**
     * Obtain the Word2Vec Configuration
     * @return The configuration object.
     */
    Word2VecConfiguration getConfiguration();

    /**
     * The number of walks to be generated per entity.
     * @return Number of walks.
     */
    int getNumberOfWalksPerEntity();
}