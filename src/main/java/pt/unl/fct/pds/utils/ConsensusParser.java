package pt.unl.fct.pds.project2.utils;

import pt.unl.fct.pds.project2.model.Node;
import java.util.Arrays;

public class ConsensusParser {
    String filename;
    
    public ConsensusParser() {}
    public ConsensusParser(String filename) {this.filename = filename;}

    public String getFilename() {return filename;}
    public void setFilename(String filename) {this.filename = filename;}

    public Node[] parseConsensus() {
        //TODO: Implement! For now just returning null.
        return null;
    }
}
