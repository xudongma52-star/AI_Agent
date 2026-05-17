package com.max.ai_agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeTool {

    @Tool(description = "获取当前的日期、时间和星期几。当需要知道今天是什么日子、计算时间差、或者生成带有日期的标题时调用此工具。")
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss E");
        return "当前时间: " + now.format(formatter);
    }
}