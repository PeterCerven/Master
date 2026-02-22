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

@Configuration
public class GraphHopperConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfig.class);

    @Value("${graphhopper.osm.file:data/slovakia-260207.osm.pbf}")
    private String osmFile;

    @Value("${graphhopper.graph.location:data/gh-cache}")
    private String graphLocation;

    private GraphHopper hopper;

    @PostConstruct
    public void init() {
        log.info("Initializing GraphHopper with OSM file: {}", osmFile);

        hopper = new GraphHopper();
        hopper.init(new com.graphhopper.GraphHopperConfig()
                .putObject("graph.encoded_values", "car_access,car_average_speed")
                .putObject("graph.location", graphLocation)
                .putObject("import.osm.ignored_highways", "footway,construction,cycleway,path,steps"));
        hopper.setOSMFile(osmFile);

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
