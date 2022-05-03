package com.datadog.appsec.dependency;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;

public interface DependencyService {
  DependencyService NOOP = new DependencyServiceNoop();

  Collection<Dependency> determineNewDependencies();

  void addURI(URI uri);

  class DependencyServiceNoop implements DependencyService {
    @Override
    public Collection<Dependency> determineNewDependencies() {
      return Collections.emptyList();
    }

    @Override
    public void addURI(URI uri) {}
  }
}
