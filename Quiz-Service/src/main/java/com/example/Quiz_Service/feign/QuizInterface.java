package com.example.Quiz_Service.feign;

import com.example.Quiz_Service.dto.QuestionSnapshotResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "Question-Service", path = "/question")
public interface QuizInterface {
  @GetMapping("/snapshot")
  List<QuestionSnapshotResponse> snapshot(
      @RequestParam("category") String category, @RequestParam("numQuestion") int count);

  @PostMapping("/snapshot/selected")
  List<QuestionSnapshotResponse> selected(@RequestBody List<String> ids);
}
