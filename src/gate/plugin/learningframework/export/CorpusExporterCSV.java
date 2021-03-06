
package gate.plugin.learningframework.export;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import gate.plugin.learningframework.Globals;
import gate.plugin.learningframework.data.Attribute;
import gate.plugin.learningframework.data.Attributes;
import gate.plugin.learningframework.data.CorpusRepresentationMallet;
import gate.plugin.learningframework.engines.Info;
import gate.plugin.learningframework.engines.Parms;
import gate.plugin.learningframework.features.FeatureExtraction;
import gate.plugin.learningframework.mallet.NominalTargetWithCosts;
import gate.util.GateRuntimeException;
import gate.util.Strings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Exporter for comma separated values and tab separated values formats.
 * 
 * @author johann
 */
public class CorpusExporterCSV extends CorpusExporter {

  @Override
  public Info getInfo() {
    Info info = new Info();
    
    info.algorithmClass = "gate.plugin.learningframework.engines.AlgorithmClassification";
    info.algorithmName = "DUMMY";
    info.engineClass = "gate.plugin.learningframework.engines.EngineDUMMY";
    info.modelClass =  "DUMMY";    
    return info;
  }

  @Override
  public void export(File directory, CorpusRepresentationMallet cr, String instanceType, String parms) {    
    InstanceList malletInstances = cr.getRepresentationMallet();
    Pipe pipe = malletInstances.getPipe();
    Attributes attrs = new Attributes(pipe,instanceType);
    // We create either one or two files: if the parameter -twofiles or -t
    // is specified, then there will be one file for the independent variables
    // and one file for the targets, otherwise the targets will be at the
    // end of each record. 
    // By default, the forst row is a header row that contains all the names
    // of the features and "target" for the target column.
    // The header lines is suppressed with the option -n or -noheader
    // The format of the file is CSV by default, but can be switched to 
    // TSV if -T or -TSV is specified.
    // TODO: If the first instance has an instance weight, then an additional file
    // for the instance weights is created.
    // TODO: add option for how to treat missing values: filter instance or 
    // use whatever value is stored or set to 0.0 or set to NaN etc??
    // TODO: add parameter that will output the target label and not the target 
    // index
    
    Parms ps = new Parms(parms, "t:twofiles:b", "n:noheader:b","T:TSV:b","s:separator:s");
    boolean twofiles = (boolean)ps.getValueOrElse("twofiles", false);
    boolean noheader = (boolean)ps.getValueOrElse("noheader", false);
    boolean tsv = (boolean)ps.getValueOrElse("TSV",false);
    // TODO: 
    boolean filterMV = false;
    String defaultSep = tsv ? "\\t" : ",";
    String separator = (String)ps.getValueOrElse("separator",defaultSep);
    separator = Strings.unescape(separator);
    String extension = tsv ? ".tsv" : ".csv";
    
    PrintStream dataOut = null;
    File dataFile = null;
    try {
      String basename = Globals.dataBasename;
      if(twofiles) {
        basename = "indep";
      }
      dataFile = new File(directory,basename+extension);
      dataOut = new PrintStream(new FileOutputStream(dataFile));
    } catch (Exception ex) {
      throw new RuntimeException("Could not open "+dataFile.getAbsolutePath(),ex);
    }
    
    PrintStream targetOut = null;
    File targetFile = null;
    if(twofiles) {
      try {
        targetFile = new File(directory,"dep"+extension);
        targetOut = new PrintStream(new FileOutputStream(targetFile));
        //System.err.println("DEBUG: opened dep file "+targetFile.getAbsolutePath());
      } catch (Exception ex) {
        throw new RuntimeException("Could not open "+targetFile.getAbsolutePath(),ex);
      }
    } else {
      targetOut = dataOut;
    }
    if(!noheader) {
      boolean firstField = true;
      for (Attribute attr : attrs) {
        if (firstField) {
          firstField = false;
        } else {
          dataOut.print(separator);
        }
        // get the name, if necessary, escape it properly
        String name = escape4CSV(attr.name);
        dataOut.print(name);
      } // for attr : attrs
      // Now add the header for the target column if we just have one file,
      // otherwise write it into the target file
      if (twofiles) {
        targetOut.println("target");
      } else {
        targetOut.print(separator);
        targetOut.println("target");
      }
    }
    int nrFeatures = pipe.getDataAlphabet().size();
    // export the actual data in dense format
    // TODO: make sure we respect the flag to ignore an instance with missing values
    // TODO: if the instance has a weight, also output the weight!!
    for(Instance inst : malletInstances) {
      // TODO
      if(filterMV) {
        Object ignore = inst.getProperty(FeatureExtraction.PROP_IGNORE_HAS_MV);    
        // If the flag says the instance should get ignored, return null
        // to indicate to the caller that this is an ignored instance.
        if(ignore != null && ignore.equals(true)) 
          continue;        
      } // filterMV
      
      /////////////////////////////////////////////////////
      
      Double instanceWeight = (Double)inst.getProperty("instanceWeight");
      Object data = inst.getData();
      if(data instanceof FeatureVector) {
        FeatureVector vector = (FeatureVector)data;
        boolean first = true;
        for(int i=0; i<nrFeatures; i++) {
          double value = vector.value(i);
          if(first) 
            first = false;
          else 
            dataOut.print(separator); 
          // TODO: depending on MV processing!!
          if(Double.isNaN(value)) {
            dataOut.print(0.0);
          } else {
            dataOut.print(value);
          }
        }
      } // for 
      // no print the target to the end of the data file or to the target file,
      // depending on our parameters. targetOut has been set to either its 
      // own file stream or dataOut if we just write a single file
      if(!twofiles) {
        targetOut.print(separator);
      } 
      Object target = inst.getTarget();
      LabelAlphabet targetAlphabet = (LabelAlphabet)inst.getTargetAlphabet();
      if(targetAlphabet != null) {
        Label tl = null;
        if(target instanceof Label) {
          tl = (Label)target;
        } else {
          tl = targetAlphabet.lookupLabel(target);
        } 
        Object entry = tl.getEntry();
        if(entry instanceof String) {
          targetOut.print(targetAlphabet.lookupIndex(entry));          
        } else if(entry instanceof NominalTargetWithCosts) {
          throw new GateRuntimeException("Cost vectors not yet implemented");
        }
      } else {
        if(target instanceof Double) {
          targetOut.print(target);
        } else  {
          throw new GateRuntimeException("No target Alphabet and odd target class: "+target.getClass());
        }
      }
      if(twofiles)
        dataOut.println();
      targetOut.println();
      
      ////////////////////////////////////////////////////
      
    }
    try {
      dataOut.close();
    } catch(Exception ex) {
      //
    }
    if(twofiles) {
      try {
        targetOut.close();
      } catch(Exception ex) {
        //
      }
    }
  } // export

  /**
   * Escape routine for the CSV format. 
   * For now this is pretty much identical to the escaping for ARFF,
   * but can get changed as necessary.
   * If the string contains a quote character, a percent character or 
   * any whitespace those characters are escaped with a backslash.
   * Also, a backslash is escaped with a backslash.
   * If any character needed to be escaped, the whole string is quoted. 
   * The string is also quoted if it contains curly braces.
   * @param what
   * @return 
   */
  public String escape4CSV(String what) {
    int len = what.length();
    what = what.replaceAll("([\"'%\\n\\r \\t\\\\])", "\\\\$1");
    if(what.length()!=len || what.contains("{") || what.contains("}")) {
      what = "'" + what + "'";
    }
    return what;
  }
  
  
}
