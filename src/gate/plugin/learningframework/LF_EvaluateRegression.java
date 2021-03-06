/*
 * Copyright (c) 1995-2015, The University of Sheffield. See the file
 * COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 * Copyright 2015 South London and Maudsley NHS Trust and King's College London
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.plugin.learningframework;

import gate.AnnotationSet;
import java.net.URL;

import org.apache.log4j.Logger;

import gate.Controller;
import gate.Document;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.plugin.learningframework.data.CorpusRepresentationMalletTarget;
import gate.plugin.learningframework.engines.AlgorithmRegression;
import gate.plugin.learningframework.engines.Engine;
import gate.plugin.learningframework.engines.EvaluationResult;
import gate.plugin.learningframework.engines.EvaluationResultRegression;
import gate.plugin.learningframework.features.FeatureSpecification;
import gate.plugin.learningframework.features.TargetType;
import gate.util.GateRuntimeException;
import java.io.File;

/**
 *
 */
@CreoleResource(
        name = "LF_EvaluateRegression",
        helpURL = "https://github.com/GateNLP/gateplugin-LearningFramework/wiki/LF_EvaluateRegression",
        comment = "Evaluate an algorithm and parameter settings for regression")
public class LF_EvaluateRegression extends LF_TrainBase {

  private static final long serialVersionUID = -420477134626830002L;

  private final Logger logger = Logger.getLogger(LF_EvaluateRegression.class.getCanonicalName());

  /**
   * The configuration file.
   *
   */
  private java.net.URL featureSpecURL;

  @RunTime
  @CreoleParameter(comment = "The feature specification file.")
  public void setFeatureSpecURL(URL featureSpecURL) {
    this.featureSpecURL = featureSpecURL;
  }

  public URL getFeatureSpecURL() {
    return featureSpecURL;
  }

  private AlgorithmRegression trainingAlgorithm;

  @RunTime
  @Optional
  @CreoleParameter(comment = "The algorithm to be used for training the classifier")
  public void setTrainingAlgorithm(AlgorithmRegression algo) {
    this.trainingAlgorithm = algo;
  }

  public AlgorithmRegression getTrainingAlgorithm() {
    return this.trainingAlgorithm;
  }

  protected ScalingMethod scaleFeatures = ScalingMethod.NONE;

  @RunTime
  @CreoleParameter(defaultValue = "NONE", comment = "If and how to scale features. ")
  public void setScaleFeatures(ScalingMethod sf) {
    scaleFeatures = sf;
  }

  public ScalingMethod getScaleFeatures() {
    return scaleFeatures;
  }

  protected String targetFeature;

  @RunTime
  @Optional
  @CreoleParameter(comment = "The feature containing the target label")
  public void setTargetFeature(String targetFeature) {
    this.targetFeature = targetFeature;
  }

  public String getTargetFeature() {
    return this.targetFeature;
  }

  private CorpusRepresentationMalletTarget corpusRepresentation = null;
  private FeatureSpecification featureSpec = null;

  private Engine engine = null;

  protected String sequenceSpan;

  
  protected EvaluationMethod evaluationMethod = EvaluationMethod.CROSSVALIDATION;
  @RunTime
  @Optional
  @CreoleParameter(comment = "Evaluation Method, not all algorithms may support all methods", defaultValue = "CROSSVALIDATION")
  public void setEvaluationMethod(EvaluationMethod val) {
    evaluationMethod = val;
  }
  
  public EvaluationMethod getEvaluationMethod() {
    return evaluationMethod;
  }
  
  
  protected int numberOfFolds = 10;
  @RunTime
  @Optional
  @CreoleParameter(comment = "Number of folds for the cross validation", defaultValue = "10")
  public void setNumberOfFolds(Integer val) {
    if(val < 2) {
      throw new GateRuntimeException("numberOfFolds must be > 1");
    }
    numberOfFolds = val;
  }
  
  public Integer getNumberOfFolds() {    
    return numberOfFolds;
  }
  
  protected double trainingFraction = 0.6667;
  @RunTime
  @Optional
  @CreoleParameter(comment = "Fraction of instances to use for training, > 0.0 and < 1.0", defaultValue = "0.6667")
  public void setTrainingFraction(Double val) {
    if(val <= 0.0 || val >= 1.0) {
      throw new GateRuntimeException("trainingFraction must be > 0.0 and < 1.0");
    }
    trainingFraction = val;
  }
  
  public Double getTrainingFraction() {
    return trainingFraction;
  }
  
  
  protected int numberOfRepeats = 1;
  @RunTime
  @Optional
  @CreoleParameter(comment = "Number of times to perform holdout evaluation to get an average", defaultValue = "1")
  public void setNumberOfRepeats(Integer val) {
    numberOfRepeats = val;
  }
  
  public Integer getNumberOfRepeats() {    
    return numberOfRepeats;
  }
  
  protected String instanceWeightFeature = "";
  @RunTime
  @Optional
  @CreoleParameter(comment = "The feature that constains the instance weight. If empty, no instance weights are used",
          defaultValue="")
  public void setInstanceWeightFeature(String val) {
    instanceWeightFeature = val;
  }
  public String getInstanceWeightFeature() { return instanceWeightFeature; }
  
  
  
  private int nrDocuments;
  
  private File dataDir;

  @Override
  public Document process(Document doc) {
    if(isInterrupted()) {
      interrupted = false;
      throw new GateRuntimeException("Execution was requested to be interrupted");
    }
    // extract the required annotation sets,
    AnnotationSet inputAS = doc.getAnnotations(getInputASName());
    AnnotationSet instanceAS = inputAS.get(getInstanceType());
    AnnotationSet sequenceAS = null;
    AnnotationSet classAS = null;
    String tfName = getTargetFeature();
    String nameFeatureName = null;
    corpusRepresentation.add(instanceAS, sequenceAS, inputAS, classAS, tfName, TargetType.NUMERIC, instanceWeightFeature, nameFeatureName);
    nrDocuments++;
    return doc;
  }

  @Override
  public void afterLastDocument(Controller arg0, Throwable t) {
    System.out.println("LearningFramework: Starting evaluating engine " + engine);
    System.out.println("Training set size: " + corpusRepresentation.getRepresentationMallet().size());
    if (corpusRepresentation.getRepresentationMallet().getDataAlphabet().size() > 20) {
      System.out.println("LearningFramework: Attributes " + corpusRepresentation.getRepresentationMallet().getDataAlphabet().size());
    } else {
      System.out.println("LearningFramework: Attributes " + corpusRepresentation.getRepresentationMallet().getDataAlphabet().toString().replaceAll("\\n", " "));
    }
      //System.out.println("DEBUG: instances are "+corpusRepresentation.getRepresentationMallet());

    corpusRepresentation.finish();
    
    EvaluationResult er = engine.evaluate(getAlgorithmParameters(),evaluationMethod,numberOfFolds,trainingFraction,numberOfRepeats);
    logger.info("LearningFramework: Evaluation complete!");
    logger.info(er);
    if(getCorpus() != null && er instanceof EvaluationResultRegression) {
      getCorpus().getFeatures().put("LearningFramework.rmse", ((EvaluationResultRegression)er).rmse);
    }
  }

  @Override
  protected void finishedNoDocument(Controller c, Throwable t) {
    logger.error("Processing finished, but no documents seen, cannot train!");
  }

  @Override
  protected void beforeFirstDocument(Controller controller) {
    if (getTrainingAlgorithm() == null) {
      throw new GateRuntimeException("LF_EvaluateRegression: no training algorithm specified");
    }
    if(getTargetFeature() == null || getTargetFeature().isEmpty()) {
      throw new GateRuntimeException("LF_EvaluateRegression: no target feature specified");
    }
    AlgorithmRegression alg = getTrainingAlgorithm();

    System.err.println("DEBUG: Before Document.");
    System.err.println("  Training algorithm engine class is " + alg.getEngineClass());
    System.err.println("  Training algorithm algor class is " + alg.getTrainerClass());

    // Read and parse the feature specification
    featureSpec = new FeatureSpecification(featureSpecURL);
    System.err.println("DEBUG Read the feature specification: " + featureSpec);

    // create the corpus representation for creating the training instances
    corpusRepresentation = new CorpusRepresentationMalletTarget(featureSpec.getFeatureInfo(), scaleFeatures, TargetType.NUMERIC);
    System.err.println("DEBUG: created the corpusRepresentationMallet: " + corpusRepresentation);

    // Create the engine from the Algorithm parameter
    engine = Engine.createEngine(trainingAlgorithm, getAlgorithmParameters(), corpusRepresentation);
    
    System.err.println("DEBUG: created the engine: " + engine);

    nrDocuments = 0;
    
    System.err.println("DEBUG: setup of the training PR complete");    
  }

}
