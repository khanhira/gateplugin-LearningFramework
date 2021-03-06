package gate.plugin.learningframework.engines;

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
public class EngineSklearnWrapper extends EngineSklearnBase {

  public EngineSklearnWrapper() {    
    WRAPPER_NAME = "SklearnWrapper";
    ENV_WRAPPER_HOME = "SKLEARN_WRAPPER_HOME";
    PROP_WRAPPER_HOME = "gate.plugin.learningframework.sklearnwrapper.home";
    YAML_FILE = "sklearn.yaml";
    YAML_SETTING_WRAPPER_HOME = "sklearnwrapper.home";
    SCRIPT_APPLY_BASENAME = "sklearnWrapperApply";
    SCRIPT_TRAIN_BASENAME = "sklearnWrapperTrain";
    SCRIPT_EVAL_BASENAME = "sklearnWrapperEval";
    MODEL_BASENAME = "sklmodel";
    MODEL_INSTANCE = new SklearnModel();
  }
  
  static class SklearnModel { }
  
}
