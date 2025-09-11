// File: Simulator.java
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Simulator {
    int totalMarcos;
    int nproc;
    List<Proceso> procesos = new ArrayList<>();
    MemoryManager mm;
    AtomicLong globalTime = new AtomicLong(0); // contador para LRU (usar long)

    public Simulator(int totalMarcos, int nproc) throws IOException {
        this.totalMarcos = totalMarcos;
        this.nproc = nproc;
        loadProcs();
        mm = new MemoryManager(totalMarcos, globalTime);
    }

    void loadProcs() throws IOException {
        for (int i = 0; i < nproc; i++) {
            Proceso p = Proceso.fromFile("proc" + i + ".txt", i);
            procesos.add(p);
        }
    }

    public void run() {
        Queue<Proceso> q = new LinkedList<>();
        q.addAll(procesos);
        // asignar marcos iniciales equitativamente
        int marcosPorProc = totalMarcos / Math.max(1, nproc);
        for (int i = 0; i < nproc; i++) mm.assignInitialFrames(i, marcosPorProc);

        while (!q.isEmpty()) {
            Proceso p = q.poll();
            if (p.hasNextReference()) {
                Reference ref = p.peekNext();
                boolean pageLoaded = mm.accessPage(p.id, ref.page, ref.isWrite(), p, globalTime);
                if (!pageLoaded) {
                    // hubo fallo: se demorará un turno (no avanzar la referencia)
                    q.add(p);
                    continue;
                } else {
                    // avanzar referencia tras acceso exitoso
                    p.consumeNext();
                    if (p.hasNextReference()) q.add(p);
                    else {
                        // proceso terminó: reasignar marcos del proceso según política
                        mm.reassignFramesAfterProcessEnds(p.id, procesos);
                    }
                }
            } else {
                // ya no tiene refs -> ya terminado
            }
        }

        // imprimir estadísticas
        for (Proceso p : procesos) {
            p.printStats();
        }
    }
}
