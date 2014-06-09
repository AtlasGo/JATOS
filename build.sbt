name := "myFirstApp"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  "org.hibernate" % "hibernate-entitymanager" % "4.2.1.Final",
  "mysql" % "mysql-connector-java" % "5.1.18"
)     

play.Project.playJavaSettings
