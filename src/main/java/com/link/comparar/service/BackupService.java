package com.link.comparar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Respaldo automático de la base de datos H2.
 * Usa el comando BACKUP de H2, que genera una copia consistente sin detener la app.
 */
@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final String PREFIX = "historico-";
    private static final String SUFFIX = ".zip";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final Path backupDir;
    private final int keep;

    public BackupService(JdbcTemplate jdbcTemplate,
                         @Value("${backup.enabled:true}") boolean enabled,
                         @Value("${backup.dir:./data/backups}") String backupDir,
                         @Value("${backup.keep:14}") int keep) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.backupDir = Paths.get(backupDir).toAbsolutePath().normalize();
        this.keep = Math.max(1, keep);
    }

    /** Respaldo al arrancar: cada deploy deja una copia. */
    @EventListener(ApplicationReadyEvent.class)
    public void backupOnStartup() {
        runBackup("arranque");
    }

    /** Respaldo diario (por defecto 3:00 a. m. hora de Colombia). */
    @Scheduled(cron = "${backup.cron:0 0 3 * * *}", zone = "America/Bogota")
    public void backupDaily() {
        runBackup("diario");
    }

    public synchronized void runBackup(String motivo) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now(ZoneId.of("America/Bogota")).format(STAMP);
            Path target = backupDir.resolve(PREFIX + stamp + SUFFIX);
            String sqlPath = target.toString().replace("\\", "/").replace("'", "''");
            jdbcTemplate.execute("BACKUP TO '" + sqlPath + "'");
            logger.info("Respaldo ({}) creado: {} ({} bytes)", motivo, target, Files.size(target));
            deleteOldBackups();
        } catch (Exception e) {
            logger.error("No se pudo crear el respaldo ({}) en {}", motivo, backupDir, e);
        }
    }

    private void deleteOldBackups() throws IOException {
        List<Path> backups;
        try (Stream<Path> files = Files.list(backupDir)) {
            backups = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
                    })
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = keep; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
            logger.info("Respaldo antiguo eliminado: {}", backups.get(i).getFileName());
        }
    }
}
