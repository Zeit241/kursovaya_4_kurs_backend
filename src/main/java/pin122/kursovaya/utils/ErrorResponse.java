package pin122.kursovaya.utils;

import lombok.Data;

import java.util.List;

@Data
public class ErrorResponse {

    private List<String> details;
}