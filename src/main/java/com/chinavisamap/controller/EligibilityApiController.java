package com.chinavisamap.controller;

import com.chinavisamap.service.PolicyRuleEngine;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/eligibility")
public class EligibilityApiController {
    private final PolicyRuleEngine engine;
    public EligibilityApiController(PolicyRuleEngine engine){this.engine=engine;}
    @PostMapping("/{code}")
    public ResponseEntity<Map<String,Object>> check(@PathVariable String code,@RequestBody Map<String,Object> input){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(engine.evaluate(code,input,false));
    }
    @PostMapping("/{code}/calculate")
    public ResponseEntity<Map<String,Object>> calculate(@PathVariable String code,@RequestBody Map<String,Object> input){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(engine.evaluate(code,input,true));
    }
}
