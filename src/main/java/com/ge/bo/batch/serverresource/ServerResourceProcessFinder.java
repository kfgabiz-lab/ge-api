package com.ge.bo.batch.serverresource;

import org.springframework.stereotype.Component;
import oshi.software.os.OSProcess;

import java.util.List;

@Component
public class ServerResourceProcessFinder {

  public OSProcess findByCommandLinePattern(List<OSProcess> processes, String pattern) {
    OSProcess best = null;
    for (OSProcess process : processes) {
      String commandLine = process.getCommandLine();
      if (commandLine != null && commandLine.contains(pattern)) {
        if (best == null || process.getResidentSetSize() > best.getResidentSetSize()) {
          best = process;
        }
      }
    }
    return best;
  }
}
