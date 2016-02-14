package org.sameersingh.scalaplot.gnuplot

import org.sameersingh.scalaplot.Style.HistogramStyle.{RowStacked, Cluster}
import org.sameersingh.scalaplot._
import collection.mutable.ArrayBuffer
import java.io.{InputStreamReader, BufferedReader, File, PrintWriter}
import Style._

/**
 * @author sameer
 * @date 10/25/12
 */
class GnuplotPlotter(chart: Chart) extends Plotter(chart) {

  var lines = new ArrayBuffer[String]
  var directory: String = ""
  var filename: String = ""
  var isFirst = false
  var isLast = false

  protected def getColorname(color: Color.Type): String = {
    import Color._
    color match {
      case Black => "black"
      case Grey => "dark-grey"
      case Red => "red"
      case Green => "web-green"
      case Blue => "web-blue"
      case Magenta => "dark-magenta"
      case Cyan => "dark-cyan"
      case Maroon => "dark-orange"
      case Mustard => "dark-yellow"
      case RoyalBlue => "royalblue"
      case Gold => "goldenrod"
      case DarkGreen => "dark-spring-green"
      case Purple => "purple"
      case SteelBlue => "steelblue"
      case Yellow => "yellow"
    }
  }

  protected def getPointType(pt: PointType.Type): Int = {
    pt match {
      case PointType.Dot => 0
      case PointType.+ => 1
      case PointType.X => 2
      case PointType.* => 3
      case PointType.emptyBox => 4
      case PointType.fullBox => 5
      case PointType.emptyO => 6
      case PointType.fullO => 7
      case PointType.emptyTri => 8
      case PointType.fullTri => 9
    }
  }

  protected def getLineStyle(s: XYSeries): String =
    getLineStyle(s.plotStyle, s.color, s.pointSize, s.pointType, s.lineWidth, s.lineType)

  protected def getLineStyle(plotStyle: XYPlotStyle.Type,
                             col: Option[Color.Type],
                             pointSize: Option[Double],
                             pointType: Option[PointType.Type],
                             lineWidth: Option[Double],
                             lineType: Option[LineType.Type]): String = {
    val sb = new StringBuffer()
    sb append "with "
    sb append (plotStyle match {
      case XYPlotStyle.Lines => "lines"
      case XYPlotStyle.LinesPoints => "linespoints"
      case XYPlotStyle.Points => "points"
      case XYPlotStyle.Dots => "dots"
      case XYPlotStyle.Impulses => "impulses"
    })
    if (col.isDefined)
      sb.append(" linecolor rgbcolor \"%s\"" format (getColorname(col.get)))
    if (pointSize.isDefined)
      sb.append(" pointsize %f" format (pointSize.get))
    if (pointType.isDefined)
      sb.append(" pointtype %d" format (getPointType(pointType.get)))
    if (lineWidth.isDefined)
      sb.append(" linewidth %f" format (lineWidth.get))
    // TODO line type
    sb.toString
  }

  protected def getFillStyle(s: BarSeries): String =
    getFillStyle(s.color, s.border, s.borderLineType, s.fillStyle, s.density, s.pattern)

  protected def getFillStyle(col: Option[Color.Type],
                             border: Option[Boolean],
                             borderLineType: Option[LineType.Type],
                             fillStyle: Option[FillStyle.Type],
                             density: Option[Double],
                             pattern: Option[Int]): String = {
    val sb = new StringBuffer()
    sb append "with histogram"
    if (col.isDefined)
      sb.append(" linecolor rgbcolor \"%s\"" format (getColorname(col.get)))
    if (border.isDefined || fillStyle.isDefined) {
      sb.append(" fill ")
      fillStyle.foreach({
        case FillStyle.Empty => sb.append("empty ")
        case FillStyle.Solid => sb.append("solid "); density.foreach(d => sb.append(d + " "))
        case FillStyle.Pattern => sb.append("pattern "); pattern.foreach(i => sb.append(i + " "))
      })
      border.foreach({
        case false => sb.append("noborder ")
        // TODO line type
        case true => sb.append("border ")
      })
    }
    sb.toString
  }

  protected def getHistogramStyle(s: HistogramStyle.Type): String = {
    s match {
      case Cluster => "cluster gap 1"
      case RowStacked => "rowstacked"
    }
  }

  def plotChart(chart: Chart, defaultTerminal: String = "dumb") {
    lines += "# Chart settings"
    chart.title.foreach(t => lines += "set title \"%s\"" format (t))
    chart.pointSize.foreach(t => lines += "set pointSize %f" format (t))
    // legend
    if (chart.showLegend) {
      lines += "set key %s %s" format(chart.legendPosX.toString.toLowerCase, chart.legendPosY.toString.toLowerCase)
    } else lines += "unset key"
    lines += "set terminal " + defaultTerminal
    lines += ""
  }

  def plotBarChart(chart: BarChart) {
    lines += "# BarChart settings"
    if (chart.y.isLog) lines += "set logscale y"
    else lines += "set nologscale"
    val yr1s = if (chart.y.min.isDefined) chart.y.min.get.toString else "*"
    val yr2s = if (chart.y.max.isDefined) chart.y.max.get.toString else "*"
    lines += "set yr [%s:%s] %sreverse" format(yr1s, yr2s, if (chart.y.isBackward) "" else "no")
    lines += "set xlabel \"%s\"" format (chart.x.label)
    lines += "set ylabel \"%s\"" format (chart.y.label)
    lines += "set style histogram %s" format (getHistogramStyle(chart.style))
    plotBarData(chart.data)
  }

  def plotBarData(data: BarData) {
    lines += "# BarData Plotting"
    lines += "set style data histogram"
    lines += "set style fill solid border -1"
    lines += "plot \\"
    var index = 0
    for (series: BarSeries <- data.serieses) {
      isFirst = (series == data.serieses.head)
      isLast = (series == data.serieses.last)
      plotBarSeries(series, index)
      index += 1
    }
    // store the data if required
    for (series: BarSeries <- data.serieses) {
      isFirst = (series == data.serieses.head)
      isLast = (series == data.serieses.last)
      postPlotBarSeries(series, data.names)
    }
    lines += ""
  }

  def plotBarSeries(series: BarSeries, index: Int) {
    series match {
      case m: MemBarSeries => plotMemBarSeries(m, index)
    }
  }

  def postPlotBarSeries(series: BarSeries, names: Int => String) {
    series match {
      case m: MemBarSeries => postPlotMemBarSeries(m, names)
    }
  }

  def plotMemBarSeries(series: MemBarSeries, index: Int) {
    val suffix = if (isLast) "" else ", \\"
    val dataFilename = if (series.isLarge) filename + "-" + series.name + ".dat" else "-"
    val using = if (index == 0) "2:xtic(3)" else "2:xtic(3)"
    val fill = getFillStyle(series)
    lines += "'%s' using %s title \"%s\" %s%s" format(dataFilename, using, series.name, fill, suffix)
  }

  def postPlotMemBarSeries(series: MemBarSeries, names: Int => String) {
    if (series.isLarge) {
      // write to file, then refer to it in the script
      series.writeToFile(directory + filename + "-" + series.name + ".dat", names)
    } else {
      // write directly to the lines
      lines += "# %s" format (series.name)
      lines ++= series.toStrings(names)
      lines += "end"
    }
  }

  def plotXYChart(chart: XYChart) {
    lines += "# XYChart settings"
    if (chart.x.isLog && chart.y.isLog) lines += "set logscale"
    else if (chart.x.isLog) lines += "set logscale x"
    else if (chart.y.isLog) lines += "set logscale y"
    else lines += "set nologscale"
    val xr1s = if (chart.x.min.isDefined) chart.x.min.get.toString else "*"
    val xr2s = if (chart.x.max.isDefined) chart.x.max.get.toString else "*"
    val yr1s = if (chart.y.min.isDefined) chart.y.min.get.toString else "*"
    val yr2s = if (chart.y.max.isDefined) chart.y.max.get.toString else "*"
    lines += "set xr [%s:%s] %sreverse" format(xr1s, xr2s, if (chart.x.isBackward) "" else "no")
    lines += "set yr [%s:%s] %sreverse" format(yr1s, yr2s, if (chart.y.isBackward) "" else "no")
    lines += "set xlabel \"%s\"" format (chart.x.label)
    lines += "set ylabel \"%s\"" format (chart.y.label)
    plotXYData(chart.data)
  }

  def plotXYData(data: XYData) {
    lines += "# XYData Plotting"
    lines += "plot \\"
    for (series: XYSeries <- data.serieses) {
      isFirst = (series == data.serieses.head)
      isLast = (series == data.serieses.last)
      plotXYSeries(series)
    }
    // store the data if required
    for (series: XYSeries <- data.serieses) {
      isFirst = (series == data.serieses.head)
      isLast = (series == data.serieses.last)
      postPlotXYSeries(series)
    }
    lines += ""
  }

  def plotXYSeries(series: XYSeries) {
    series match {
      case m: MemXYSeries => plotMemXYSeries(m)
      case f: FileXYSeries => plotFileXYSeries(f)
    }
  }

  def postPlotXYSeries(series: XYSeries) {
    series match {
      case m: MemXYSeries => postPlotMemXYSeries(m)
      case f: FileXYSeries => postPlotFileXYSeries(f)
    }
  }

  def plotMemXYSeries(series: MemXYSeries) {
    val suffix = if (isLast) "" else ", \\"
    val dataFilename = if (series.isLarge) filename + "-" + series.name + ".dat" else "-"
    val everyString = if (!series.every.isDefined) "" else "every %d" format (series.every.get)
    lines += "'%s' %s using %d:%d title \"%s\" %s %s" format(dataFilename, everyString, 1, 2, series.name, getLineStyle(series), suffix)
  }

  def plotFileXYSeries(series: FileXYSeries) {
    val suffix = if (isLast) "" else ", \\"
    val everyString = if (!series.every.isDefined) "" else "every %d" format (series.every.get)
    lines += "'%s' %s using %d:%d title \"%s\" %s %s" format(series.dataFilename, everyString, series.xcol, series.ycol, series.name, getLineStyle(series), suffix)
  }

  def postPlotMemXYSeries(series: MemXYSeries) {
    if (series.isLarge) {
      // write to file, then refer to it in the script
      series.writeToFile(directory + filename + "-" + series.name + ".dat")
    } else {
      // write directly to the lines
      lines += "# %s" format (series.name)
      lines ++= series.toStrings()
      lines += "end"
    }
  }

  def reset = {
    lines.clear
    directory = ""
    filename = ""
    isFirst = false
    isLast = false
  }

  def postPlotFileXYSeries(series: FileXYSeries) {}

  def writeScriptFile(directory: String, filenamePrefix: String, terminal: String,
                      filenameSuffix: String, stdout: Boolean = false, defaultTerminal: String = "dumb") {
    // write the description
    assert(new File(directory).isDirectory, directory + " should be a directory")
    assert(directory.endsWith("/"), directory + " should end with a /")
    reset
    this.directory = directory
    filename = filenamePrefix
    plotChart(chart, terminal)
    lines += "set terminal %s" format (terminal)
    if (stdout) lines += "set output"
    else lines += "set output \"%s\"" format (filename + "." + filenameSuffix)
    chart match {
      case xyc: XYChart => plotXYChart(xyc)
      case bc: BarChart => plotBarChart(bc)
    }
    lines += "unset output"
    lines += "# Wrapup"
    lines += "set terminal %s" format (defaultTerminal)
    lines += "refresh"

    val scriptFile = directory + filenamePrefix + ".gpl"
    val writer = new PrintWriter(scriptFile)
    for (line <- lines) {
      writer.println(line)
    }
    writer.close()
  }

  override def pdf(directory: String, filenamePrefix: String) {
    val monochromeString = if (chart.monochrome) "monochrome" else ""
    val sizeString = if (chart.size.isDefined) "size %f,%f" format(chart.size.get._1, chart.size.get._2) else ""
    val terminal = "pdf enhanced linewidth 3.0 %s %s" format(monochromeString, sizeString)
    writeScriptFile(directory, filenamePrefix, terminal, "pdf")
    runGnuplot(directory, filenamePrefix)
  }

  override def png(directory: String, filenamePrefix: String) {
    if (chart.monochrome) println("Warning: Monochrome ignored.")
    val sizeString = if (chart.size.isDefined) "size %f,%f" format(chart.size.get._1, chart.size.get._2) else ""
    val terminal = "png enhanced %s" format (sizeString)
    writeScriptFile(directory, filenamePrefix, terminal, "png")
    runGnuplot(directory, filenamePrefix)
  }

  override def svg(directory: String, filenamePrefix: String): String = {
    if (chart.monochrome) println("Warning: Monochrome ignored.")
    val sizeString = if (chart.size.isDefined) "size %f,%f" format(chart.size.get._1, chart.size.get._2) else ""
    val terminal = "svg enhanced linewidth 3.0 %s" format (sizeString)
    writeScriptFile(directory, filenamePrefix, terminal, "svg", true, "unknown")
    runGnuplot(directory, filenamePrefix)
  }

  def string(directory: String, filenamePrefix: String): String = {
    val terminal = "dumb enhanced"
    writeScriptFile(directory, filenamePrefix, terminal, "txt", true, "unknown")
    runGnuplot(directory, filenamePrefix)
  }

  def html(directory: String, filenamePrefix: String) {
    if (chart.monochrome) println("Warning: Monochrome ignored.")
    val sizeString = if (chart.size.isDefined) "size %f,%f" format(chart.size.get._1, chart.size.get._2) else ""
    val terminal = "canvas enhanced %s" format (sizeString)
    writeScriptFile(directory, filenamePrefix, terminal, "html")
    runGnuplot(directory, filenamePrefix)
  }

  def js(directory: String, filenamePrefix: String): String = {
    if (chart.monochrome) println("Warning: Monochrome ignored.")
    val sizeString = if (chart.size.isDefined) "size %f,%f" format(chart.size.get._1, chart.size.get._2) else ""
    val terminal = "canvas enhanced name\"%s\"" format (filenamePrefix)
    writeScriptFile(directory, filenamePrefix, terminal, "js")
    runGnuplot(directory, filenamePrefix)
    htmlWrap(directory, filenamePrefix)
  }

  def js2(directory: String, filenamePrefix: String): String = {
    // write the description
    assert(new File(directory).isDirectory, directory + " should be a directory")
    assert(directory.endsWith("/"), directory + " should end with a /")
    reset
    this.directory = directory
    filename = filenamePrefix
    plotChart(chart)
    chart match {
      case xyc: XYChart => plotXYChart(xyc)
    }
    lines += "# Wrapup"
    lines += "set terminal canvas enhanced name \"%s\"" format (filenamePrefix)
    lines += "set output \"%s\"" format (filename + ".js")
    lines += "refresh"
    lines += "unset output"
    val scriptFile = directory + filenamePrefix + ".gpl"
    val writer = new PrintWriter(scriptFile)
    for (line <- lines) {
      writer.println(line)
    }
    writer.close()
    val str = runGnuplot(directory, filenamePrefix)
    htmlWrap(directory, filenamePrefix)
  }

  private def htmlWrap(directory: String, filenamePrefix: String, jsDir: String = "/usr/local/Cellar/gnuplot/4.6.5/share/gnuplot/4.6/js") = {
    """
      |   <html>
      |       <head>
      |           <script src="%s/canvastext.js"></script>
      |           <script src="%s/gnuplot_common.js"></script>
      |           <script src="%s/gnuplot_mouse.js"></script>
      |       </head>
      |       <body onload="%s();">
      |           <script src="%s.js"></script>
      |           <canvas id="%s" width=600 height=400>
      |               <div id="err_msg">No support for HTML 5 canvas element</div>
      |           </canvas>
      |       </body>
      |   </html>
    """.stripMargin format(jsDir, jsDir, jsDir, filenamePrefix, filenamePrefix, filenamePrefix)
  }

  def runGnuplot(directory: String, filenamePrefix: String): String = {
    var line: String = ""
    var output = ""
    val cmdLine = "gnuplot " + filenamePrefix + ".gpl"

    try {
      val p = Runtime.getRuntime().exec(cmdLine, Array.empty[String], new File(directory))
      val input = new BufferedReader(new InputStreamReader(p.getInputStream()))
      while (({
        line = input.readLine();
        line
      }) != null) {
        output += (line + '\n')
      }
      input.close()
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    output
  }
}

object GnuplotPlotter {
  def pdf(chart: Chart, directory: String, filePrefix: String): Unit = new GnuplotPlotter(chart).pdf(directory, filePrefix)

  def html(chart: Chart, directory: String, filePrefix: String): Unit = new GnuplotPlotter(chart).html(directory, filePrefix)

  def js(chart: Chart, directory: String, filePrefix: String): Unit = new GnuplotPlotter(chart).js(directory, filePrefix)

  def png(chart: Chart, directory: String, filePrefix: String): Unit = new GnuplotPlotter(chart).png(directory, filePrefix)
}
