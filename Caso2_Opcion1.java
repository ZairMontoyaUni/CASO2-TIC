import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class Caso2_Opcion1 {
    
    static int TP;
    static int NPROC;
    static ArrayList<String> TAMS = new ArrayList<>();

    public static void main(String[] args) throws NumberFormatException, IOException {
        String path = "";
        parseConfig(path);
        findVDs(TP, NPROC, TAMS);
    }

    public static void parseConfig(String path) throws NumberFormatException, IOException {
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("TP=")) {
                    TP = Integer.parseInt(line.substring(3).trim());
                } else if (line.startsWith("NPROC=")) {
                    NPROC = Integer.parseInt(line.substring(6).trim());
                } else if (line.startsWith("TAMS=")) {
                    String valores = line.substring(5).trim();
                    String[] arr = valores.split(",");
                    for (String s : arr) {
                        TAMS.add(s.trim());
                    }
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.out.println("Invalid path");
        }

    }

    public static void findVDs(int pageSize, int numProcesses, ArrayList<String> sizes) {
        for (int k = 0; k < sizes.size(); k++) {
            String fileName = "proc" + k + ".txt";

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
                String dimensions = sizes.get(k);
                int numRows = Integer.parseInt(dimensions.split("x")[0]);
                int numColumns = Integer.parseInt(dimensions.split("x")[1]);
                int numReferences = numRows*numColumns*3;
                int numPages = (int) Math.ceil((float) numReferences*4/pageSize);

                writer.println("TP=" + pageSize);
                writer.println("NF=" + numRows);
                writer.println("NC=" + numColumns);
                writer.println("NR=" + numReferences);
                writer.println("NP=" + numPages);

                for (int i = 0; i < numRows; i++) {
                    for (int j = 0; j < numColumns; j++) {
                        int pos = (i*numColumns + j)*4;
                        int M1page = (int) Math.floor((float) (pos)/pageSize);
                        int M2page = (int) Math.floor((float) (numRows*numColumns*4 + pos)/pageSize);
                        int M3page = (int) Math.floor((float) (numRows*numColumns*8 + pos)/pageSize);
                        writer.println("M1: [" + i + "-" + j + "], " + M1page + ", " + (pos % pageSize) + ", r");
                        writer.println("M2: [" + i + "-" + j + "], " + M2page + ", " + ((numRows * numColumns * 4 + pos) % pageSize) + ", r");
                        writer.println("M3: [" + i + "-" + j + "], " + M3page + ", " + ((numRows * numColumns * 8 + pos) % pageSize) + ", w");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
