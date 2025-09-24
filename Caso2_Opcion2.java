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
    static HashMap<Integer, Integer> realFrames = new HashMap<>();
    static Queue<Process> processes = new LinkedList<>();
    static HashMap<Integer, Process> finishedProcesses = new HashMap<>();
    static long globalTime = 0L;

    static class Process {
        int ID;
        int TP;
        int NF;
        int NC;
        int NR;
        int NP;
        ArrayList<String> processList = new ArrayList<>();
        HashMap<Integer, Integer> pageTable = new HashMap<>();
        ArrayList<Integer> frames = new ArrayList<>();
        int line = 0;
        int fails = 0;
        int hits = 0;
        int swap = 0;
        HashMap<Integer, Long> lastAccess = new HashMap<>();
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        String pathArg = args.length > 0 ? args[0] : System.getProperty("user.dir");
        Scanner sc = new Scanner(System.in);
        System.out.println("Ingrese la cantidad de procesos: ");
        numProcesses = sc.nextInt();
        System.out.println("Ingrese la cantidad de marcos en memoria real, recuerde que tiene que ser multiplo de la cantidad de procesos: ");
        numFrames = sc.nextInt();
        sc.close();

        System.out.println("Inicio. BasePath = " + pathArg);
        parseDVs(pathArg, numFrames);
        System.out.println("Simulacion:");
        simulate(processes);
        System.out.println("Archivo generado exitosamente");
    }

    public static void parseDVs(String basePath, int frames) throws NumberFormatException, IOException {
        
        for (int i = 0; i < numProcesses; i++) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(basePath + "\\proc" + i + ".txt"));
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
                            p.lastAccess.put(k, globalTime);
                        }
                        System.out.println("PROC " + i + "leyendo TP. Num Paginas: " + p.NP);
                    } else if (line.startsWith("M1:") || (line.startsWith("M2:")) || (line.startsWith("M3:"))) {
                        p.processList.add(line);
                    }
                }
                p.ID = i;
                System.out.println("PROC " + i + " == Termino de leer archivo de configuracion ==");
                int frameStart = (frames/numProcesses) * i;
                int frameFinish = frameStart + (frames/numProcesses);
                for (int j = frameStart; j < frameFinish; j++) {
                    System.out.println("Proceso " + i + ": recibe marco " + j);
                    realFrames.put(j, -1);
                    p.frames.add(j);
                }
                processes.add(p);
                br.close();
            } catch (FileNotFoundException e) {
                System.out.println("Invalid path");
            }
        }
    }

    private static void simulate(Queue<Process> processes) {
        while (!processes.isEmpty()) {
            globalTime += 1L;
            Process p = processes.remove();
            String insLine = p.processList.get(p.line);
            if (!((p.line + 1) > p.processList.size())) {
                System.out.println("Turno proc: " + p.ID);
                System.out.println("PROC " + p.ID + " analizando linea_:" + p.line);
                int page = Integer.parseInt(insLine.split(",")[1].trim());
                if (p.pageTable.get(page) != -1) {
                    p.hits++;
                    p.lastAccess.put(page, globalTime);
                    p.line++;
                    System.out.println("PROC " + p.ID + " hits: " + p.hits);
                } else {
                    p.fails++;
                    p.swap++;
                    p.hits--;
                    System.out.println("PROC " + p.ID + " falla de pag: " + page);
                    boolean replace = true;
                    for (Integer frame : p.frames) {
                        if (realFrames.get(frame) == -1 && (replace == true)) {
                            realFrames.put(frame, page);
                            p.pageTable.put(page, frame);
                            replace = false;
                        }
                    }
                    if (replace) {
                        p.swap ++;
                        lruReplace(p, page);
                    }
                }
                System.out.println("PROC " + p.ID + " envejecimiento");

                if (p.line == p.processList.size()) {
                    System.out.println("========================");
                    System.out.println("Termino proc: " + p.ID);
                    System.out.println("========================");
                    int candidateID = -1;
                    int candidateFails = -1;
                    for (int i = 0; i < processes.size(); i++) {
                        Process cp = processes.remove();
                        if (cp.fails > candidateFails) {
                            candidateID = cp.ID;
                            candidateFails = cp.fails;
                        }
                        processes.add(cp);
                    }

                    for (int i = 0; i < processes.size(); i++) {
                        Process cp = processes.remove();
                        if (cp.ID == candidateID) {
                            for (int frame : p.frames) {
                                System.out.println("PROC " + p.ID + " removiendo marco: " + frame);
                                realFrames.put(frame, -1);
                                cp.frames.add(frame);
                            }
                            for (int frame : p.frames) {
                                System.out.println("PROC " + cp.ID + " asignando marco nuevo: " + frame);
                            }
                        }
                        processes.add(cp);
                    }
                    finishedProcesses.put(p.ID, p);

                } else {
                    processes.add(p);
                }
            }
        }
        createfiles();
    }

    private static void lruReplace(Process p, int page) {
        
        int victimPage = -1;
        long minAccess = Long.MAX_VALUE;
        int victimFrame = -1;

        for (Integer pg : p.pageTable.keySet()) {
            Integer f = p.pageTable.get(pg);
            long la = p.lastAccess.get(pg);
            if (la < minAccess && f != -1) {
                minAccess = la;
                victimPage = pg;
                victimFrame = f;
            }
        }
        p.pageTable.put(victimPage, -1);
        realFrames.put(victimFrame, page);
        p.pageTable.put(page, victimFrame); 
    }

    private static void createfiles() {
        String fileName = "salida.txt";
        

        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {

            for (int i = 0; i < numProcesses; i++) {
                Process p = finishedProcesses.get(i);
                float tasaFallas = (float)p.fails / (float)p.NR;
                float tasaExito = 1 - tasaFallas;

                writer.println("Proceso: " + i);
                writer.println("- Num referencias: " + p.NR);
                writer.println("- Fallas: " + p.fails);
                writer.println("- Hits: " + p.hits);
                writer.println("- SWAP: " + p.swap);
                writer.println("- Tasa fallas: " + String.format("%.4f", tasaFallas));
                writer.println("- Tasa Exito: " + String.format("%.4f", tasaExito));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}