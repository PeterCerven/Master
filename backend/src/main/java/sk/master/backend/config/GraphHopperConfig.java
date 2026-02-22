package sk.master.backend.config;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GraphHopperConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfig.class);

    @Value("${graphhopper.osm.files}")
    private List<String> osmFiles;

    @Value("${graphhopper.graph.location:data/gh-cache}")
    private String graphLocation;

    private GraphHopper hopper;

    @PostConstruct
    public void init() {
        log.info("Initializing GraphHopper with OSM files: {}", osmFiles);

        hopper = new GraphHopper();
        hopper.setOSMFile(String.join(",", osmFiles));
        hopper.setGraphHopperLocation(graphLocation);

        hopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));

        hopper.importOrLoad();
        log.info("GraphHopper initialized. Nodes: {}, Edges: {}", hopper.getBaseGraph().getNodes(), hopper.getBaseGraph().getEdges());
    }

    @Bean
    public GraphHopper graphHopper() {
        return hopper;
    }

    @PreDestroy
    public void close() {
        if (hopper != null) {
            hopper.close();
            log.info("GraphHopper closed.");
        }
    }
}
