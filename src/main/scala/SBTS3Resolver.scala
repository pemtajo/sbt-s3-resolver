import sbt._
import Keys._

object SbtS3Resolver extends Plugin {

  type S3Credentials = (String, String)

  lazy val s3credentialsFile = 
    SettingKey[Option[String]]("s3-credentials-file", 
      "properties format file with amazon credentials to access S3")
 
  lazy val s3credentials = 
    SettingKey[Option[S3Credentials]]("s3-credentials", 
      "S3 credentials accessKey and secretKey")

  // parsing credentials from the file
  def s3credentialsParser(file: Option[String]): Option[S3Credentials] = {

    file map { f: String =>
      val path = new java.io.File(f)
      val p = new java.util.Properties
      p.load(new java.io.FileInputStream(path))
      ( p.getProperty("accessKey")
      , p.getProperty("secretKey") )
    }

  }

  case class S3Resolver(
      name: String
    , url: String
    , patterns: Patterns = Resolver.defaultPatterns
    ) {

    // for proper serialization
    override def toString = 
      """s3resolver(\"%s\", \"%s\", %s)""" format 
        (name, url, patternsToString(patterns))

    private def patternsToString(ps: Patterns): String =
      "Patterns(%s, %s, %s)" format (
        seqToString(ps.ivyPatterns)
      , seqToString(ps.artifactPatterns)
      , ps.isMavenCompatible
      )

    private def seqToString(s: Seq[String]): String = 
      s.mkString("Seq(\\\"", "\\\", \\\"", "\\\")")


    // setting up normal sbt resolver depending on credentials
    def toSbtResolver(credentials: S3Credentials): Resolver = {

      val r = new ohnosequences.ivy.S3Resolver()

      r.setName(name)
      
      def withBase(pattern: String): String = 
        if(url.endsWith("/") || pattern.startsWith("/")) url + pattern 
        else url + "/" + pattern

      patterns.ivyPatterns.foreach{ p => r.addIvyPattern(withBase(p)) }
      patterns.artifactPatterns.foreach{ p => r.addArtifactPattern(withBase(p)) }

      r.setAccessKey(credentials._1)
      r.setSecretKey(credentials._2)
      new sbt.RawRepository(r)

    }

  }

  // default values
  override def settings = Seq(
    s3credentialsFile in Global := None
  , s3credentials     in Global <<= s3credentialsFile (s3credentialsParser)
  )
} 
