/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.plugin.learningframework.data;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.SparseVector;
import gate.util.GateRuntimeException;
import libsvm.svm_node;
import libsvm.svm_problem;

/**
 *
 * @author Johann Petrak
 */
public class CorpusRepresentationLibSVM extends CorpusRepresentation {

  protected svm_problem data;
  protected CorpusRepresentationMallet crm;

  public CorpusRepresentationLibSVM(CorpusRepresentationMallet other) {
    data = getFromMallet(other);
    crm = other;
  }

  public svm_problem getRepresentationLibSVM() {
    return data;
  }

  @Override
  public Object getRepresentation() {
    return data;
  }

  public static svm_node[] libSVMInstanceIndepFromMalletInstance(
          cc.mallet.types.Instance malletInstance) {

    // TODO: maybe check that data is really a sparse vector? Should be in all cases
    // except if we have an instance from MalletSeq
    SparseVector data = (SparseVector) malletInstance.getData();
    int[] indices = data.getIndices();
    double[] values = data.getValues();
    svm_node[] nodearray = new svm_node[indices.length];
    int index = 0;
    for (int j = 0; j < indices.length; j++) {
      svm_node node = new svm_node();
      node.index = indices[j]+1;   // NOTE: LibSVM locations have to start with 1
      node.value = values[j];
      nodearray[index] = node;
      index++;
    }
    return nodearray;
  }

  /**
   * Create libsvm representation from Mallet.
   *
   * @param instances
   * @return
   */
  public static svm_problem getFromMallet(CorpusRepresentationMallet crm) {
    InstanceList instances = crm.getRepresentationMallet();
    svm_problem prob = new svm_problem();
    int numTrainingInstances = instances.size();
    prob.l = numTrainingInstances;
    prob.y = new double[prob.l];
    prob.x = new svm_node[prob.l][];

    for (int i = 0; i < numTrainingInstances; i++) {
      Instance instance = instances.get(i);

      //Labels
      // convert the target: if we get a label, convert to index,
      // if we get a double, use it directly
      Object tobj = instance.getTarget();
      if (tobj instanceof Label) {
        prob.y[i] = ((Label) instance.getTarget()).getIndex();
      } else if (tobj instanceof Double) {
        prob.y[i] = (double) tobj;
      } else {
        throw new GateRuntimeException("Odd target in mallet instance, cannot convert to LIBSVM: " + tobj);
      }

      //Features
      SparseVector data = (SparseVector) instance.getData();
      int[] indices = data.getIndices();
      double[] values = data.getValues();
      prob.x[i] = new svm_node[indices.length];
      for (int j = 0; j < indices.length; j++) {
        svm_node node = new svm_node();
        node.index = indices[j]+1; // NOTE: LibSVM location indices have to start with 1
        node.value = values[j];
        prob.x[i][j] = node;
      }
    }
    return prob;
  }

  @Override
  public void clear() {
    // NOTE: ok, for LibSVM there is not much other info that could be kept, we just 
    // set this to null for now. 
    // There is not much reason why this should ever get used anyway.
    data = null;
  }

  @Override
  public InstanceList getRepresentationMallet() {
   return crm.instances;
  }

}
