package tests;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("tests")
@IncludeClassNamePatterns(".*")          // incluye todas las clases de tests
@ExcludeClassNamePatterns(".*RunAllTests") // ✅ evita incluir esta Suite dentro de sí misma
public class RunAllTests { }