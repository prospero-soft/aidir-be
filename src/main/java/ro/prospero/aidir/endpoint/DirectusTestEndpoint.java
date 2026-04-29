package ro.prospero.aidir.endpoint;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.service.DirectusService;
import ro.prospero.aidir.service.ToolSubmissionService;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("api/directus")
public class DirectusTestEndpoint {
    private DirectusService directusService;
    private ToolSubmissionService toolSubmissionService;
    private List<ToolDTO> toolDtos;

    @GetMapping
    @ResponseBody
    public String get() {
//        directusService.createMetricsCollection();
//        directusService.createLoyaltyCollection();


        return "OK";
    }

    @GetMapping("memory")
    @ResponseBody
    public String memorySizeCalculator() {
//        List<ToolDTO> tools = toolSubmissionService.getQueue();
//
//        Long total = tools.stream().reduce(0L,
//                                            (acc, tool) -> acc + instrumentation.getObjectSize(tool), Long::sum);
//        List<Map<String, Long>> sizes = tools.stream().map(tool -> Map.of(tool.getName(), instrumentation.getObjectSize(tool))).toList();
//
//        String pretty = sizes.stream().map(m -> Arrays.toString(m.entrySet().toArray())).collect(Collectors.joining("\\n"));
//
        this.toolDtos = toolSubmissionService.getQueue();

        return "OK" ;
    }

}
