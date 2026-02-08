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
        log.info("Inicializujem GraphHopper s OSM súborom: {}", osmFile);

        hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphLocation);

        // Registruj encoded values, ktoré chceme čítať z hrán
        hopper.setEncodedValuesString("car_access, car_average_speed, road_class, road_environment, max_speed, surface");
        hopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));

        hopper.importOrLoad();
        log.info("GraphHopper inicializovaný. Nodes: {}, Edges: {}", hopper.getBaseGraph().getNodes(), hopper.getBaseGraph().getEdges());
    }

    @Bean
    public GraphHopper graphHopper() {
        return hopper;
    }

    @PreDestroy
    public void close() {
        if (hopper != null) {
            hopper.close();
            log.info("GraphHopper zatvorený.");
        }
    }
}
