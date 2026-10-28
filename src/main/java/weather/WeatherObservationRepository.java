package weather;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, Long> {
    List<WeatherObservation> findByCityIgnoreCaseOrderByForecastDateDesc(String city);
}
