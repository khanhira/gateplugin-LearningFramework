package gate.plugin.learningframework.engines;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import gate.Annotation;
import gate.AnnotationSet;
import gate.lib.interaction.data.SparseDoubleVector;
import gate.lib.interaction.process.Process4JsonStream;
import gate.lib.interaction.process.Process4ObjectStream;
import gate.lib.interaction.process.ProcessBase;
import gate.lib.interaction.process.ProcessSimple;
import gate.plugin.learningframework.EvaluationMethod;
import gate.plugin.learningframework.Exporter;
import gate.plugin.learningframework.GateClassification;
import gate.plugin.learningframework.Globals;
import gate.plugin.learningframework.data.CorpusRepresentationMalletTarget;
import gate.plugin.learningframework.mallet.LFPipe;
import gate.util.GateRuntimeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * An engine that represents Python Scikit Learn through en external process.
 * 
 * This requires that the user configures the location of where sklearn-wrapper is installed.
 * This can be done by setting the environment variable SKLEARN_WRAPPPER_HOME, the Java property
 * gate.plugin.learningframework.sklearnwrapper.home or by adding another yaml file "sklearn.yaml" 
 * to the data directory which contains the setting sklearnwrapper.home.
 * If the path starts with a slash
 * it is an absolute path, otherwise the path is resolved relative to the 
 * directory. 
 * 
 * 
 * @author Johann Petrak
 */
public class EngineSklearnExternal extends Engine {

  ProcessBase process;
  
  /**
   * Try to find the script running the sklearn-Wrapper command.
   * If apply is true, the executable for application is searched,
   * otherwise the one for training.
   * This checks the following settings (increasing priority): 
   * environment variable SKLEARN_WRAPPER_HOME,
   * java property gate.plugin.learningframework.sklearnwrapper.home and
   * the setting "sklearnwrapper.home" in file "sklearn.yaml" in the data directory,
   * if it exists. 
   * The setting for the sklearn wrapper home can be relative in which case it
   * will be resolved relative to the dataDirectory
   * @param dataDirectory
   * @return 
   */
  private File findWrapperCommand(File dataDirectory, boolean apply) {
    String homeDir = System.getenv("SKLEARN_WRAPPER_HOME");
    String tmp = System.getProperty("gate.plugin.learningframework.sklearnwrapper.home");
    if(tmp!=null) homeDir = tmp;
    File sklearnInfoFile = new File(dataDirectory,"sklearn.yaml");
    if(sklearnInfoFile.exists()) {
      Yaml yaml = new Yaml();
      Object obj;
      try {
        obj = yaml.load(new InputStreamReader(new FileInputStream(sklearnInfoFile),"UTF-8"));
      } catch (Exception ex) {
        throw new GateRuntimeException("Could not load yaml file "+sklearnInfoFile,ex);
      }    
      tmp = null;
      if(obj instanceof Map) {
        Map map = (Map)obj;
        tmp = (String)map.get("sklearnwrapper.home");      
      } else {
        throw new GateRuntimeException("Info file has strange format: "+sklearnInfoFile.getAbsolutePath());
      }
      if(tmp == null) {
        System.err.println("sklearn.yaml file present but does not contain sklearnwrapper.home setting");
      } else {
        homeDir = tmp;
      }      
    }
    if(homeDir == null) {
      throw new GateRuntimeException("SklearnWrapper home not set, please see https://github.com/GateNLP/gateplugin-LearningFramework/wiki/UsingSklearn");
    }
    File wrapperHome = new File(homeDir);
    if(!wrapperHome.isAbsolute()) {
      wrapperHome = new File(dataDirectory,homeDir);
    }
    if(!wrapperHome.isDirectory()) {
      throw new GateRuntimeException("SklearnWrapper home is not a directory: "+wrapperHome.getAbsolutePath());
    }
    // Now, depending on the operating system, and on train/apply,
    // find the correct script to execute
    File commandFile;
    // we use the simple heuristic that if the file separator is "/" 
    // we assume we can use the bash script, if it is "\" we use the windows
    // script and otherwise we give up
    boolean linuxLike = System.getProperty("file.separator").equals("/");
    boolean windowsLike = System.getProperty("file.separator").equals("\\");
    if(linuxLike) {
      if(apply) 
        commandFile = new File(new File(wrapperHome,"bin"),"sklearnWrapperApply.sh");
      else
        commandFile = new File(new File(wrapperHome,"bin"),"sklearnWrapperTrain.sh");
    } else if(windowsLike) {
      if(apply) 
        commandFile = new File(new File(wrapperHome,"bin"),"sklearnWrapperApply.cmd");
      else
        commandFile = new File(new File(wrapperHome,"bin"),"sklearnWrapperTrain.cmd");      
    } else {
      throw new GateRuntimeException("It appears this OS is not supported");
    }
    commandFile = commandFile.isAbsolute() ? 
            commandFile :
            new File(dataDirectory,commandFile.getPath());
    if(!commandFile.canExecute()) {
      throw new GateRuntimeException("Not an executable file or not found: "+commandFile+" please see https://github.com/GateNLP/gateplugin-LearningFramework/wiki/UsingSklearn");
    }
    return commandFile;
  }
  
  
  @Override
  protected void loadModel(File directory, String parms) {
    // Instead of loading a model, this establishes a connection with the 
    // external sklearn process. 
    File commandFile = findWrapperCommand(directory, true);
    String modelFileName = new File(directory,"sklmodel").getAbsolutePath();
    String finalCommand = commandFile.getAbsolutePath()+" "+modelFileName;
    System.err.println("Running: "+finalCommand);
    // Create a fake Model jsut to make LF_Apply... happy which checks if this is null
    model = "ExternalSklearnWrapperModel";
    process = new Process4JsonStream(directory,finalCommand);
  }

  @Override
  protected void saveModel(File directory) {
    // NOTE: we do not need to save the model here because the external
    // sklearnWrapper command does this.
    // However we still need to make sure a usable info file is saved!
    info.engineClass = EngineSklearnExternal.class.getName();
    info.save(directory);
  }

  @Override
  public void trainModel(File dataDirectory, String instanceType, String parms) {
    // invoke the sklearn wrapper for training
    // NOTE: for this the first word in parms must be the full sklearn class name, the rest are parms
    if(parms == null || parms.isEmpty()) {
      throw new GateRuntimeException("Cannot train using SklearnWrapper, algorithmParameter must contain fulle SciKit Learn algorithm class name as first word");
    }
    String sklearnClass = null;
    String sklearnParms = "";
    int spaceIdx = parms.indexOf(" ");
    if(spaceIdx<0) {
      sklearnClass = parms;
    } else {
      sklearnClass = parms.substring(0,spaceIdx);
      sklearnParms = parms.substring(spaceIdx).trim();
    }
    File commandFile = findWrapperCommand(dataDirectory, false);
    // Export the data 
    // Note: any scaling was already done in the PR before calling this method!
    // find out if we train classification or regression
    // TODO: NOTE: not sure if classification/regression matters here as long as
    // the actual exporter class does the right thing based on the corpus representation!
    Exporter.export(getCorpusRepresentationMallet(), 
            Exporter.EXPORTER_MATRIXMARKET2_CLASS, dataDirectory, instanceType, parms);
    String dataFileName = dataDirectory.getAbsolutePath()+File.separator;
    String modelFileName = new File(dataDirectory, "sklmodel").getAbsolutePath();
    String finalCommand = commandFile.getAbsolutePath()+" "+dataFileName+" "+modelFileName+" "+sklearnClass+" "+sklearnParms;
    System.err.println("Running: "+finalCommand);
    // Create a fake Model jsut to make LF_Apply... happy which checks if this is null
    model = "ExternalSklearnWrapperModel";
    
    process = new ProcessSimple(dataDirectory,finalCommand);
    process.waitFor();
  }

  @Override
  public EvaluationResult evaluate(String algorithmParameters, EvaluationMethod evaluationMethod, int numberOfFolds, double trainingFraction, int numberOfRepeats) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public List<GateClassification> classify(AnnotationSet instanceAS, AnnotationSet inputAS, AnnotationSet sequenceAS, String parms) {
    CorpusRepresentationMalletTarget data = (CorpusRepresentationMalletTarget)corpusRepresentationMallet;
    data.stopGrowth();
    int nrCols = data.getPipe().getDataAlphabet().size();
    //System.err.println("Running EngineSklearn.classify on document "+instanceAS.getDocument().getName());
    List<GateClassification> gcs = new ArrayList<GateClassification>();
    LFPipe pipe = (LFPipe)data.getRepresentationMallet().getPipe();
    ArrayList<String> classList = null;
    // If we have a classification problem, pre-calculate the class label list
    if(pipe.getTargetAlphabet() != null) {
      classList = new ArrayList<String>();
      for(int i = 0; i<pipe.getTargetAlphabet().size(); i++) {
        String labelstr = (String) pipe.getTargetAlphabet().lookupObject(i);
        classList.add(labelstr);
      }
    }
    // create the datastructure we need for the application script: 
    // a map that contains the following fields:
    // - cmd: either STOP or CSR1
    // - values: the non-zero values, for increasing rows and increasing cols within rows
    // - rowinds: for the k-th value which row number it is in
    // - colinds: for the k-th value which column number (location index) it is in
    // - shaperows: number of rows in total
    // - shapecols: maximum number of cols in a vector
    Map map = new HashMap<String,Object>();
    map.put("cmd", "CSR1");
    ArrayList<Double> values = new ArrayList<Double>();
    ArrayList<Integer> rowinds = new ArrayList<Integer>();
    ArrayList<Integer> colinds = new ArrayList<Integer>();
    int rowIndex = 0;
    List<Annotation> instances = instanceAS.inDocumentOrder();
    for(Annotation instAnn : instances) {
      Instance inst = data.extractIndependentFeatures(instAnn, inputAS);
      
      //FeatureVector fv = (FeatureVector)inst.getData();      
      //System.out.println("Mallet instance, fv: "+fv.toString(true)+", len="+fv.numLocations());
      inst = pipe.instanceFrom(inst);
      
      FeatureVector fv = (FeatureVector)inst.getData();
      //System.out.println("Mallet instance, fv: "+fv.toString(true)+", len="+fv.numLocations());
      
      // Convert to the sparse vector we use to send to the weka process
      int locs = fv.numLocations();
      SparseDoubleVector sdv = new SparseDoubleVector(locs);
      for(int i=0;i<locs;i++) {
        int index = fv.indexAtLocation(i);
        values.add(fv.value(index));
        rowinds.add(rowIndex);
        colinds.add(index);
      }
      rowIndex++;
    }
    // send the matrix data over to the weka process
    map.put("values", values);
    map.put("rowinds", rowinds);
    map.put("colinds",colinds);
    map.put("shaperows", rowIndex);
    map.put("shapecols",nrCols);
    process.writeObject(map);
    // get the result back
    Object ret = process.readObject();
    Map<String,Object> response = null;
    if(ret instanceof Map) {
      response = (Map)ret;
    }
    if(response == null) {
      throw new RuntimeException("Got a response from Sklearn process which cannot be used: "+response);
    }
    // the response has the following format:
    // - status: should be "OK" or an error message
    // - targets: a vector of target indices/values
    // - probas: if probabilities are supported, a vector of vectors of class probabilities, otherwise null
    
    String status = (String)response.get("status");
    if(status == null || !status.equals("OK")) {
      throw new RuntimeException("Status of response is not OK but "+status);
    }
    ArrayList<Double> targets = (ArrayList<Double>)response.get("targets");
    ArrayList<ArrayList<Double>> probas = (ArrayList<ArrayList<Double>>)response.get("probas");
    
    GateClassification gc = null;
    
    // now check if the mallet representation and the weka process agree 
    // on if we have regression or classification
    if(pipe.getTargetAlphabet() == null) {
      // we expect a regression result, i.e probas should be null
      if(probas != null) {
        throw new RuntimeException("We think we have regression but the Sklearn process sent probabilities");
      }
    }
    // now go through all the instances again and do the target assignment from the vector(s) we got
    int instNr = 0;
    for(Annotation instAnn : instances) {
      if(pipe.getTargetAlphabet() == null) { // we have regression
        gc = new GateClassification(instAnn, targets.get(instNr));
      } else {
        int bestlabel = targets.get(instNr).intValue();
        String cl
                = (String) pipe.getTargetAlphabet().lookupObject(bestlabel);
        double bestprob = Double.NaN;
        if(probas != null) {
          bestprob = Collections.max(probas.get(instNr));
        }
        gc = new GateClassification(
                instAnn, cl, bestprob, classList, probas.get(instNr));
      }
      gcs.add(gc);
      instNr++;
    }
    data.startGrowth();
    return gcs;
  }

  @Override
  public void initializeAlgorithm(Algorithm algorithm, String parms) {
    // do not do anything
  }

  @Override
  protected void loadMalletCorpusRepresentation(File directory) {
    corpusRepresentationMallet = CorpusRepresentationMalletTarget.load(directory);
  }
  
}
