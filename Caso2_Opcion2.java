import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class Caso2_Opcion2 {
    
    static int numFrames;
    static int numProcesses;
    static Queue<Integer> directions;
    static ArrayList<Process> processes;

    static class Process {
        int TP;
        int NF;
        int NC;
        int NR;
        int NP;
        Queue<String> processList = new LinkedList<>();
        HashMap<Integer, Integer> pageTable = new HashMap<>();
        ArrayList<Integer> frames = new ArrayList<>();
        int wait = 0;
        boolean finished = false;
        int fails = 0;
        int hits = 0;
        int swap = 0;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        Scanner sc = new Scanner(System.in);
        numProcesses = sc.nextInt();
        numFrames = sc.nextInt();
        sc.close();
        System.out.println("Inicio:");
        String path = "C:\\Users\\th850\\OneDrive\\Documentos\\Universidad\\QuintoSemestre\\TIC\\Caso_02\\CASO2-TIC\\proc";
        parseDVs(path, numFrames);
        System.out.println("Simulacion:");
        simulate(processes);
    }

    public static void parseDVs(String basePath, int frames) throws NumberFormatException, IOException {
        for (int i = 0; i < numProcesses; i++) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(basePath + i + ".txt"));
                String line;
                Process p = new Process();
                System.out.println("PROC " + i + " == Leyendo archivo de configuracion ==");
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("TP=")) {
                        p.TP = Integer.parseInt(line.substring(3).trim());
                        System.out.println("PROC " + i + "leyendo TP. Tam paginas: " + p.TP);
                    } else if (line.startsWith("NF=")) {
                        p.NF = Integer.parseInt(line.substring(3).trim());
                        System.out.println("PROC " + i + "leyendo TP. Num filas: " + p.NF);
                    } else if (line.startsWith("NC=")) {
                        p.NC = Integer.parseInt(line.substring(3).trim());
                        System.out.println("PROC " + i + "leyendo TP. Num cols: " + p.NC);
                    } else if (line.startsWith("NR=")) {
                        p.NR = Integer.parseInt(line.substring(3).trim());
                        System.out.println("PROC " + i + "leyendo TP. Num Referencias: " + p.NR);
                    } else if (line.startsWith("NP=")) {
                        p.NP = Integer.parseInt(line.substring(3).trim());
                        for (int k = 0; k < p.NP; k++) {
                            p.pageTable.put(k, -1);
                        }
                        System.out.println("PROC " + i + "leyendo TP. Num Paginas: " + p.NP);
                    } else if (line.startsWith("M1: ") || (line.startsWith("M2: ")) || (line.startsWith("M3: "))) {
                        p.processList.add(line);
                    }
                }
                System.out.println("PROC " + i + " == Termino de leer archivo de configuracion ==");
                int frameStart = (frames/numProcesses) * i;
                int frameFinish = frameStart + (frames/numProcesses);
                for (int j = frameStart; j < frameFinish; j++) {
                    System.out.println("Proceso " + i + ": recibe marco " + j);
                    p.frames.add(j);
                }
                br.close();
            } catch (FileNotFoundException e) {
                System.out.println("Invalid path");
            }
        }
    }

    public static void simulate(ArrayList<Process> processes) {
        while (true) {
            for (int i = 0; i < processes.size(); i++) {
                Process p = processes.get(i);
                if (p.wait == 0) {
                    
                } else {
                    p.wait--;
                }
            }
        }
    }
}
