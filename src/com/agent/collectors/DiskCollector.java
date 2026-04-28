package com.agent.collectors;

import com.agent.metrics.DiskStats;
import java.io.*;
import java.util.*;

public class DiskCollector {

    public static DiskStats getDiskStats() {
        long readSectors  = 0;
        long writeSectors = 0;
        Map<String, long[]> perDevice = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("/proc/diskstats"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 14) continue;

                String dev = parts[2];
                if (!isBaseDevice(dev)) continue;

                long reads  = Long.parseLong(parts[5]);
                long writes = Long.parseLong(parts[9]);
                long readMs = Long.parseLong(parts[6]);
                long writeMs= Long.parseLong(parts[10]);

                readSectors  += reads;
                writeSectors += writes;
                perDevice.put(dev, new long[]{reads / 2, writes / 2, readMs, writeMs});
            }

            return new DiskStats(readSectors / 2, writeSectors / 2, perDevice);

        } catch (Exception e) {
            Logger.error("DiskCollector", e);
            return new DiskStats(-1, -1, Collections.emptyMap());
        }
    }

    /**
     * Returns true for base block devices only.
     *
     * Accepted patterns:
     *   sda, sdb, sdc ...          (SCSI/SATA)
     *   vda, vdb ...               (VirtIO)
     *   xvda, xvdb ...             (Xen)
     *   hda, hdb ...               (old IDE)
     *   nvme0n1, nvme1n2 ...       (NVMe - ends in digit but is a base device)
     *   mmcblk0, mmcblk1 ...       (eMMC - same)
     *
     * Rejected:
     *   sda1, sda2 ...             (partitions of SCSI)
     *   nvme0n1p1, nvme0n1p2 ...   (partitions of NVMe - contain 'p' before trailing digit)
     *   mmcblk0p1 ...              (partitions of eMMC)
     *   loop*, ram*, dm-*, md*
     */
    private static boolean isBaseDevice(String dev) {
        if (dev.startsWith("loop") || dev.startsWith("ram")
         || dev.startsWith("dm-") || dev.startsWith("md")
         || dev.startsWith("sr"))   return false;

        // NVMe base: nvme[0-9]+n[0-9]+ with no 'p' suffix
        if (dev.matches("nvme\\d+n\\d+"))       return true;
        // NVMe partition: nvme[0-9]+n[0-9]+p[0-9]+
        if (dev.matches("nvme\\d+n\\d+p\\d+"))  return false;

        // eMMC base: mmcblk[0-9]+
        if (dev.matches("mmcblk\\d+"))          return true;
        // eMMC partition: mmcblk[0-9]+p[0-9]+
        if (dev.matches("mmcblk\\d+p\\d+"))     return false;

        // SCSI/SATA/VirtIO/Xen/IDE base: letters + letters, no trailing digit
        // sda, vda, xvda, hda — the name ends in a letter
        if (dev.matches("[a-z]+"))              return true;

        // Otherwise (sda1, hdb2, etc.) — partition, skip
        return false;
    }
}
