package com.fyndig.recall.config;

import com.fyndig.recall.ingest.FeedType;
import com.fyndig.recall.ingest.IngestionService;
import com.fyndig.recall.repo.SupplierRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads the demo scenario on first start. The files deliberately arrive in different formats from
 * different "systems" (supplier portal CSV, WMS semicolon-CSV, MES JSON, OMS pipe-CSV) to exercise
 * the ingestion validation.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final IngestionService ingestion;
    private final SupplierRepo suppliers;
    private final ResourceLoader resources;

    @Value("${app.seed.enabled:true}") private boolean enabled;
    @Value("${app.seed.location:classpath:seed/}") private String location;

    public SeedLoader(IngestionService ingestion, SupplierRepo suppliers, ResourceLoader resources) {
        this.ingestion = ingestion; this.suppliers = suppliers; this.resources = resources;
    }

    record Feed(FeedType type, String system, String file) {}

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        if (suppliers.count() > 0) { log.info("Database already populated - skipping seed"); return; }
        List<Feed> feeds = List.of(
                new Feed(FeedType.SUPPLIERS, "ERP", "suppliers.csv"),
                new Feed(FeedType.SUPPLIER_LOTS, "SUPPLIER_PORTAL", "supplier_lots.csv"),
                new Feed(FeedType.RECEIPTS, "WMS", "goods_receipts.csv"),
                new Feed(FeedType.MOVEMENTS, "MES", "lot_movements.json"),
                new Feed(FeedType.DISPATCHES, "OMS", "dispatches.csv"));
        for (Feed f : feeds) {
            Resource r = resources.getResource(location + f.file());
            if (!r.exists()) { log.warn("Seed file {} not found at {}", f.file(), location); continue; }
            try (var in = r.getInputStream()) {
                ingestion.ingest(f.type(), f.system(), f.file(), in);
            }
        }
        log.info("Seed scenario loaded. Open http://localhost:8080 and simulate a recall of supplier lot AB-7781.");
    }
}
