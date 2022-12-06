package io.boomerang.migration;

import com.github.cloudyrock.mongock.Mongock;

public interface BoomerangMigration {
  public Mongock mongock();
  public String getCollectionPrefix();
}
