import processing.core.*; 
import processing.xml.*; 

import java.awt.*; 
import java.awt.event.*; 
import javax.swing.*; 
import javax.swing.event.*; 
import javax.swing.colorchooser.*; 
import javax.swing.plaf.metal.*; 
import java.util.jar.*; 
import thingm.linkm.*; 
import processing.serial.*; 
import javax.swing.colorchooser.*; 
import javax.swing.*; 
import javax.swing.event.*; 
import java.awt.Color; 
import javax.swing.border.*; 

import java.applet.*; 
import java.awt.Dimension; 
import java.awt.Frame; 
import java.awt.event.MouseEvent; 
import java.awt.event.KeyEvent; 
import java.awt.event.FocusEvent; 
import java.awt.Image; 
import java.io.*; 
import java.net.*; 
import java.text.*; 
import java.util.*; 
import java.util.zip.*; 
import java.util.regex.*; 

public class BlinkMSequencer2 extends PApplet {

// Copyright (c) 2007-2009, ThingM Corporation
//
// MultiBlinkMSequencerLinkM --  Multi track sequencer for BlinkM using LinkM
// =========================
// 
// A. Use case for LinkM connect + disconnect:
// 1. on startup, scan for linkm.
// 2. if linkm found, 
//    a. connect 
//    b. no dialog
//    c. change connect button to "disconnect"  // ("connected to linkm")
// 3. if linkm NOT found, change button to "connect" 
// 4. if linkm error occurs while in use,
//    a. set connectFailed=true
//    b. change button to "connect failed"
//    c. pop up dialog box on script stop, offering to reconnect
//
// B. Use case for Arduino connect + disconnect:
// 1. on startup, scan for linkm as before, follow steps A1,2,3
// 2. on connect button press, show connect dialog
// 3. connect dialog contains two radio buttons: linkm & arduino w/blinkmcommuni
//    a. arduino select has combobox of serial ports 
// 4. on arduino select
//    a. verify connect
//    b. close dialog
//    c. change connect button to "disconnect" // ("connected to arduino")
// 
// 
// 
// To-do:
// - tune fade time for both real blinkm & preview
// - tune & test other two loop durations 
// - research why timers on windows are slower (maybe use runnable)
// - need to deal with case of *no* serial ports available
//













final static String VERSION = "003";
final static String versionInfo="version "+VERSION+" \u00a9 ThingM Corporation";

final static int debugLevel = 0;

Log l = new Log();

LinkM linkm = new LinkM();  // linkm obj only used in this file
BlinkMComm2 blinkmComm = new BlinkMComm2();

boolean connected = false;   // FIXME: verify semantics correct on this
boolean blinkmConnected = false;
boolean arduinoMode = false;  // using Arduino+BlinkMComm instead of LinkM

long lastConnectCheck;

//String romScriptsDir;  // set to dataPath(".");
//File   romScripts[];   // list of ROM scripts that can fill a track
String silkfontPath = "slkscrb.ttf";  // in "data" directory
String textfontPath = "HelveticaNeue-CondensedBold.ttf";
Font silk8font;
Font textBigfont;
Font textSmallfont;
File lastFile;  // last file (if any) used to save or load

JFrame mf;  // the main holder of the app
JColorChooser colorChooser;
MultiTrackView multitrack;
ButtonPanel buttonPanel;
JPanel connectPanel;
JFileChooser fc;
JLabel statusLabel;
JLabel heartbeatLabel;
JLabel currChanIdLabel;
JLabel currChanLabel;

MenuItem itemConnect;

SetChannelDialog setChannelDialog;

final int maxScriptLength = 48;

// number of slices in the timeline == number of script lines written to BlinkM
int numSlices = maxScriptLength;  
int numTracks = 8;    // number of different blinkms

int blinkmStartAddr = 9;

// overall dimensions
int mainWidth  = 900; // 955; //950; //860;
int mainHeight = 490; //630;  // was 455
int mainHeightAdjForWindows = 30; // fudge factor for Windows layout variation
int mainWidthAdjForWindows = 10; // fudge factor for Windows layout variation


// maps loop duration in seconds to per-slice duration and fadespeed,
// both in BlinkM ticks (1/30th of a second, 33.33 msecs)
// 48 slices in a loop, thus each slice is "(duration/48)" secs long
// e.g. 100 sec duration =>  2.0833 secs/slice
// FIXME: something is wrong here.  is tick res really 1/30th sec?
// fadespeed should be chosen so color hits middle of slice
public static class Timing  {
  public int  duration;     // seconds for entire loop
  public byte durTicks;     // per cell duration in ticks (1/30th of a second) 
  public byte fadeSpeed;    // fadespeed between cell in ticks
  public Timing(int d,byte t,byte f) { duration=d; durTicks=t; fadeSpeed=f; }
}

// the supported track durations
public static Timing[] timings = new Timing [] {
    new Timing(   3, (byte)  2, (byte) 100 ),
    new Timing(  30, (byte) 18, (byte)  25 ),
    new Timing( 100, (byte) 25, (byte)   5 ),
    new Timing( 300, (byte) 75, (byte)   2 ),
    new Timing( 600, (byte)150, (byte)   1 ),
 };

int durationCurrent = timings[0].duration;


PApplet p;
Util util = new Util();  // can't be a static class because of getClass() in it

Color cBlack       = new Color(0,0,0);               // black like my soul
Color cFgLightGray = new Color(230, 230, 230);
Color cBgLightGray = new Color(200, 200, 200); //new Color(0xD1, 0xD3, 0xD4); 
Color cBgMidGray   = new Color(140, 140, 140);
Color cBgDarkGray  = new Color(100, 100, 100);
Color cDarkGray    = new Color( 90,  90,  90);
Color tlDarkGray   = new Color(55,   55,  55);       // dark color for timeline
Color cHighLight   = new Color(255,   0,   0);       // used for selections
Color cBriOrange   = new Color(0xFB,0xC0,0x80);      // bright yellow/orange
Color cMuteOrange  = new Color(0xBC,0x83,0x45);
Color cMuteOrange2 = new Color(0xF1,0x9E,0x34);

Color cEmpty   = tlDarkGray;
// colors for SetChannelDialog
Color[] setChannelColors = new Color [] {
  new Color( 0xff,0x00,0x00 ),
  new Color( 0x00,0xff,0x00 ),
  new Color( 0x00,0x00,0xff ),
  new Color( 0xff,0xff,0x00 ),
  new Color( 0xff,0x00,0xff ),
  new Color( 0x00,0xff,0xff ),
  new Color( 0x80,0xff,0xff ),
  new Color( 0xff,0xff,0xff ),
};

//
// FIXME: no, should let you adjust timeadj & repeats too
// 
String[] startupScriptNames = new String [] {
  "Script 0: Editable script",
  "Script 1: Red->Green->Blue",
  "Script 2: White flash",
  "Script 3: Red flash",
  "Script 4: Green flash",
  "Script 5: Blue flash",
  "Script 6: Cyan flash",
  "Script 7: Magenta flash",
  "Script 8: Yellow flash",
  "Script 9: Black (off)",
  "Script 10: Hue Cycle",
  "Script 11: Mood Light",
  "Script 12: Virtual Candle",
  "Script 13: Water Reflections",
  "Script 14: Old Neon",
  "Script 15: The Seasons",
  "Script 16: Thunderstorm",
  "Script 17: Stop Light",
  "Script 18: SOS",
};

/**
 * Processing's setup()
 */
public void setup() {
  size(5, 5);   // Processing's frame, we'll turn off in a bit, must be 1st line
  frameRate(25);   // each frame we can potentially redraw timelines

  l.setLevel( debugLevel );

  try { 
    // load up the lovely silkscreen font
    InputStream in = getClass().getResourceAsStream(silkfontPath);
    Font dynamicFont = Font.createFont(Font.TRUETYPE_FONT, in);
    silk8font = dynamicFont.deriveFont( 8f );

    in = getClass().getResourceAsStream(textfontPath);
    dynamicFont = Font.createFont(Font.TRUETYPE_FONT, in);
    textBigfont = dynamicFont.deriveFont( 16f );
    textSmallfont = dynamicFont.deriveFont( 13f );

    // use a Swing look-and-feel that's the same across all OSs
    MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
    UIManager.setLookAndFeel( new MetalLookAndFeel() );
  } 
  catch(Exception e) { 
    l.error("drat: "+e);
  }

  String osname = System.getProperty("os.name");
  if( !osname.toLowerCase().startsWith("mac") ) {
    mainHeight += mainHeightAdjForWindows;
    mainWidth  += mainWidthAdjForWindows;
  }
  
  p = this;
  
  setupGUI();

  bindKeys();

}

/**
 * Processing's draw()
 */
public void draw() {
  if( frameCount < 9 ) {
    super.frame.setVisible(false);  // turn off Processing's frame
    super.frame.toBack();
    mf.setVisible(true);
    mf.toFront();                   // bring ours forward  
  }
  long millis = System.currentTimeMillis();

  if( frameCount > 10 && ((millis-lastConnectCheck) > 1000) ) {
    if( arduinoMode ) {
      heartbeat();
    } 
    else {
      if( verifyLinkM() ) {
        heartbeat();
      }
    }
    lastConnectCheck = millis;
  }

  float millisPerTick = (1/frameRate) * 1000;
  // tick tock
  multitrack.tick( millisPerTick );
  // not exactly 1/frameRate, but good enough for now

  setStatus();

}


/*
 * hmm, maybe can override PApplet.handleDisplay() to get around 
 * Processing startup weirdness
 *
synchronized public void handleDisplay() {
  if( frameCount==0 ) {
    setup();
  }
  else {
    draw();
  }
}
*/

// ----------------------------------------------------------------------------

/**
 *
 */
public void setStatus()
{
  if( arduinoMode ) { 
    if( connected ) {
      setStatus("Connected to Arduino");
      buttonPanel.enableButtons(true);
    } 
    return;
  }
  
  if( connected ) {
    if( blinkmConnected ) {
      setStatus("LinkM connected, BlinkM found");
      buttonPanel.enableButtons(true);
    }
    else {
      setStatus("LinkM connected, no BlinkM found");
      buttonPanel.enableButtons(false);
    }
  } else {
    setStatus( "Disconnected. Plug in LinkM or choose File->Connect for Arduino" );
    buttonPanel.enableButtons(false);
  }
}

/**
 *
 */
public void showHelp() {
    String helpstr = "<html>"+
      "<table border=0 cellpadding=10 cellspacing=10><tr><td>"+
      "<h2> BlinkMSequencer Help </h2>"+
      "<h3> Edit Menu </h3>"+
      "<ul>"+
      "<li>Make Gradient <br/>"+
      "-- create a smooth gradient between start & and colors of a selection"+
      "<li>Edit Channel IDs <br/>"+
      "-- Edit the label and I2C address a channel sends on"+
      "</ul>"+
      "<h3> Tools Menu </h3>"+
      "<ul>"+
      "<li>BlinkM Factory Reset <br/> "+
      "-- Reset BlinkM(s) on selected channels to factory condition"+
      "<li>Set BlinkM Startup Script to... <br/>"+
      "-- Set BlinkM to play a built-in ROM script instead of the programmable one"+
      "<li>Scan I2C Bus <br/> "+
      "-- Scan I2C bus on all I2C addresses, looking for devices"+
      "<li>Change BlinkM I2C Address <br/>"+
      "-- Change the I2C address of the currently selected BlinkM"+
      "<li>Display Versions <br/>"+
      "-- Show LinkM version and BlinKM version for the selected channel"+
      "<li>Reset LinkM <br/>"+
      "-- Perform complete reset of LinkM"+
      "</ul>"+
      "</ul>"+
      "</td></tr></table>"+
      "</html>\n";

    JDialog dialog = new JDialog(mf, "BlinkMSequencer Help", false);
    
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(cBgLightGray); //sigh, gotta do this on every panel
    panel.setBorder( BorderFactory.createMatteBorder(10,10,10,10, cBgDarkGray));
    JLabel help = new JLabel(helpstr);
    panel.add( new JLabel(helpstr) );

    dialog.getContentPane().add(panel);

    dialog.setPreferredSize( new Dimension(600,450));
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(mf); // center it on the BlinkMSequencer
    dialog.pack();
    dialog.setVisible(true);

}

/**
 *
 */
public void displayVersions() {
  //if( !connected ) return;
  String msg = "Versions:\n";

  Track t = multitrack.getCurrTrack();
  int addr = t.blinkmaddr;
  try { 
    msg += "LinkM : ";
    byte[] linkmver  = linkm.getLinkMVersion();
    if( linkmver != null ) {
      msg += "0x"+ hex(linkmver[0]) + ", 0x"+ hex(linkmver[1]);
    } else {
      msg += "-could not be read-";
    }
    msg += "\n";
  } catch( IOException ioe ) {
    msg += "-No BlinkM-\n";
  }
   
  try {
    msg += "BlinkM : ";
    if( addr != -1 ) {
      byte[] blinkmver = linkm.getVersion( addr );
      if( blinkmver != null ) { 
        msg += (char)blinkmver[0]+","+(char)blinkmver[1];
      } else {
        msg += "-could not be read-\n";
      }
    }
    msg += "   (trk:"+(multitrack.currTrack+1)+", addr:"+addr +")\n";
  } catch( IOException ioe ) {
    msg += "-No BlinkM-\n";
  }

  if( arduinoMode ) {
    msg = "Display Versions only supported with LinkM.";
  }
  JOptionPane.showMessageDialog(mf, msg, "LinkM / BlinkM Versions",
                                JOptionPane.INFORMATION_MESSAGE);
}

/**
 *
 */
public void upgradeLinkMFirmware() {
  l.debug("upgradeLinkMFirmware");
  String msg = "-disabled. please use command-line tool-";
  /*
  linkm.goBootload();
  linkm.delay(2000);
  linkm.bootload("link.hex",true);
  */
  JOptionPane.showMessageDialog(mf, msg, "Upgrade LinkM Firmware",
                                JOptionPane.INFORMATION_MESSAGE);
}

/**
 *
 */
public void resetLinkM() {
  l.debug("resetLinkM");
  if( multitrack.playing ) multitrack.reset();
  if( connected ) linkm.close();

  String msg = "Reset LinkM...";
  try { 
    linkm.open();
  } catch( IOException ioe ) {
    msg += "\ncouldn't open LinkM";
    linkm = null;
  }
  try { 
    if( linkm!=null ) linkm.goBootload();
  } catch( IOException ioe ) {
    msg += "\ncouldn't switch to LinkMBoot mode\n";
  }

  linkm.pause(3000);
  try { 
    if( linkm!=null ) linkm.bootloadReset();
  } catch(IOException ioe ) {
    println("oops " +ioe);
    msg += "\ncouldn't switch back to LinkM mode\n";
  }

  connect(true);

  msg += "done";
  JOptionPane.showMessageDialog(mf, msg, "Reset LinkM",
                                JOptionPane.INFORMATION_MESSAGE);
}

/**
 *
 */
public void doI2CScan() {
  l.debug("doI2CScan");
  int start_addr = 1;
  int end_addr = 113;
  //String msg = "no devices found";
  HashSet addrset = new HashSet();
  byte[] addrs  = null;
  try {
    if( arduinoMode ) {
      addrs = blinkmComm.i2cScan( start_addr, end_addr );
    } else { 
      addrs = linkm.i2cScan( start_addr, end_addr);
    }

    int cnt = addrs.length;
    if( cnt>0 ) {
      //msg = "Found "+cnt+" devices:\n";
      for( int i=0; i<cnt; i++) {
        byte a = addrs[i];
        addrset.add( new Integer(a) );
        //msg += "addr: "+a;
      }
      //msg += "\nDone.";
    }
  } catch( IOException ioe) {
    JOptionPane.showMessageDialog(mf,
                                  "No LinkM found.\nI2C Scan cancelled.\n"+
                                  "Plug LinkM in at any time and try again.",
                                  "LinkM Not Found",
                                  JOptionPane.WARNING_MESSAGE);
    return;
  }

  // ugh, surely there's a better way to do this
  int stride = (end_addr-start_addr)/4;
  JPanel panel = new JPanel();
  panel.setLayout( new GridLayout( 2+stride, 8, 5,5) );
  panel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
  panel.setBorder( BorderFactory.createEmptyBorder(20,20,20,20) );
  JLabel lh1a = new JLabel("addr");  //lh1a.setFont(silk8font);
  JLabel lh1b = new JLabel("dev");   //lh1b.setFont(silk8font);
  JLabel lh2a = new JLabel("addr");  //lh2a.setFont(silk8font);
  JLabel lh2b = new JLabel("dev");   //lh2b.setFont(silk8font);
  JLabel lh3a = new JLabel("addr");  //lh3a.setFont(silk8font);
  JLabel lh3b = new JLabel("dev");   //lh3b.setFont(silk8font);
  JLabel lh4a = new JLabel("addr");  //lh4a.setFont(silk8font);
  JLabel lh4b = new JLabel("dev");   //lh4b.setFont(silk8font);
  panel.add( lh1a );  panel.add( lh1b );
  panel.add( lh2a );  panel.add( lh2b );
  panel.add( lh3a );  panel.add( lh3b );
  panel.add( lh4a );  panel.add( lh4b );
  int i = 0;
  do { 
    Integer a1 = start_addr+(stride*0) + i;
    Integer a2 = start_addr+(stride*1) + i;
    Integer a3 = start_addr+(stride*2) + i;
    Integer a4 = start_addr+(stride*3) + i;
    String r1 = (addrset.contains(a1)) ? "x" : ".";
    String r2 = (addrset.contains(a2)) ? "x" : ".";
    String r3 = (addrset.contains(a3)) ? "x" : ".";
    String r4 = (addrset.contains(a4)) ? "x" : ".";

    JLabel l1a = new JLabel(""+a1);    //l1a.setFont(silk8font) ;
    JLabel l1b = new JLabel(r1);       //l1b.setFont(silk8font) ;
    JLabel l2a = new JLabel(""+a2);    //l2a.setFont(silk8font) ;
    JLabel l2b = new JLabel(r2);       //l2b.setFont(silk8font) ;
    JLabel l3a = new JLabel(""+a3);    //l3a.setFont(silk8font) ;
    JLabel l3b = new JLabel(r3);       //l3b.setFont(silk8font) ;
    JLabel l4a = new JLabel(""+a4);    //l4a.setFont(silk8font) ;
    JLabel l4b = new JLabel(r4);       //l4b.setFont(silk8font) ;
    panel.add( l1a );  panel.add( l1b );
    panel.add( l2a );  panel.add( l2b );
    panel.add( l3a );  panel.add( l3b );
    panel.add( l4a );  panel.add( l4b );
    i++;
  } while( i < stride );


  JDialog dialog = new JDialog(mf, "I2C Bus Scan Results", false);
  dialog.getContentPane().add(panel);
  //dialog.setPreferredSize( new Dimension(200,400));
  dialog.setResizable(false);
  dialog.setLocationRelativeTo(null); // center it on the BlinkMSequencer
  dialog.pack();
  dialog.setVisible(true);
  
} // doI2Cscan

/**
 *
 */
public void doFactoryReset() { 
  l.debug("doFactoryReset");
  Track t = multitrack.getCurrTrack();
  int addr = t.blinkmaddr;
  String msg = "No BlinkM selected!";
  if( addr != -1 ) {
    try { 
      if( arduinoMode ) {
        blinkmComm.doFactoryReset(addr);
        blinkmComm.playScript(addr);
      } else {
        linkm.doFactoryReset(addr);
        linkm.playScript(addr);
      }
      msg = "BlinkM reset to factory defaults";
    } catch(IOException ioe ) {
      msg = "Error talking to BlinkM";
    }
  }
  JOptionPane.showMessageDialog(mf, msg, "LinkM Factory Reset",
                                JOptionPane.INFORMATION_MESSAGE);
  try {
    if( arduinoMode ) {
      blinkmComm.off(addr);
    } else { 
      linkm.off(addr);
    }
  } catch(IOException ioe) {}

}

/**
 *
 */
public void setBootScript( int scriptnum ) {
  int fadespeed = 8; // default in blinkm_nonvol.h
  if( !connected ) return;

  int addr = multitrack.getCurrTrack().blinkmaddr;
  String msg = "No BlinkM selected!";
  if( addr != -1 ) {
    try { 
      if( arduinoMode ) { 
        blinkmComm.setStartupParams( addr, 1, scriptnum, 0, fadespeed, 0);
        blinkmComm.setFadeSpeed( addr, fadespeed);
        blinkmComm.playScript( addr, scriptnum, 0,0 );
      }
      else { 
        // set boot params   addr, mode,script_id,reps,fadespeed,timeadj
        linkm.setStartupParams( addr, 1, scriptnum, 0, fadespeed, 0 );
        linkm.setFadeSpeed( addr, fadespeed);
        linkm.playScript( addr, scriptnum, 0,0 );
      }

      msg = "BlinkM at addr#"+addr+" set to light script #"+scriptnum;

    } catch(IOException ioe ) {
      msg = "Error talking to BlinkM";
    }
  } // good addr

  JOptionPane.showMessageDialog(mf, msg, "BlinkM Script Set",
                                JOptionPane.INFORMATION_MESSAGE);

  try {
    if( arduinoMode ) {
      blinkmComm.off(addr);
    } else { 
      linkm.off(addr);
    }
  } catch(IOException ioe) {}

  
}

static int verifyCount = 0;
/**
 * Verify a LinkM is present
 * @return true if LinkM is present, regardless of 'connected' status
 */
public boolean verifyLinkM() {
  if( multitrack.playing ) return true;  // punt if playing
  //l.debug("verifyLinkM:"+verifyCount++);
  if( connected ) {
    try {
      linkm.getLinkMVersion();
      l.debug("verifyLinkM: connected");
      return true;  // if above completes, we're truly connected
    } catch(IOException ioe) {
      l.debug("verifyLinkM:closing and connected");
      connected = false;
      //linkm.close();  // FIXME FIXME FIXME: this causes dump for i2cScan()
    }
  }
  else {  // else, we're not connected, so try a quick open and close
    try {
      l.debug("verifyLinkM: not connected, trying open");
      linkm.open();
      linkm.getLinkMVersion();
    } catch( IOException ioe ) {
      return false;
    }

    l.debug("verifyLinkM:connecting");
    connect(false);
  }
  return true;
}

/**
 * Open up the LinkM and set it up if it hasn't been
 * Sets and uses the global variable 'connected'
 */
public boolean connect(boolean openlinkm) {
  l.debug("connect");
  try { 
    if( openlinkm ) linkm.open();
    linkm.i2cEnable(true);
    byte[] addrs = linkm.i2cScan(1,113);
    int cnt = addrs.length;
    if( cnt>0 ) {
      /*
      multitrack.disableAllTracks();   // enable tracks for blinkms found
      for( int i=0; i<cnt; i++) {
        byte a = addrs[i];
        if( a >= blinkmStartAddr && a < blinkmStartAddr + numTracks ) {
          multitrack.toggleTrackEnable( a - blinkmStartAddr);
        }
      }

      // FIXME: should dialog popup saying blinkms found but not in right addr?
      if( addrs[0] > blinkmStartAddr ) { // FIXME: hack
        int trknum = (addrs[0] - blinkmStartAddr);  // select 1st used trk
        multitrack.currTrack = trknum;
        multitrack.selectSlice( trknum, 0, true );
        multitrack.repaint();
      }
      */
      linkm.stopScript( 0 ); // stop all scripts
      linkm.fadeToRGB(0, 0,0,0);
     
      blinkmConnected = true;
    }
    else {
      l.debug("no blinkm found!"); 
      blinkmConnected = false;
    }
  } catch(IOException ioe) {
    l.debug("connect: no linkm?  "+ioe);
    /*
    JOptionPane.showMessageDialog(mf,
                                  "No LinkM found.\n"+
                                  "Plug LinkM in at any time and "+
                                  "it will auto-connect.",
                                  "LinkM Not Found",
                                  JOptionPane.WARNING_MESSAGE);
    */
    //disconnectedMode = true;
    connected = false;
    return false;
  }
  connected = true;
  return true; // connect successful
}



/**
 * Sends a single color to a single BlinkM, using the "Fade to RGB" function
 * Used during live playback and making blinkm match preview
 * @param blinkmAddr  i2c address of a blinkm
 * @param c color to send
 */
public void sendBlinkMColor( int blinkmAddr, Color c ) {
  //l.debug("sendBlinkMColor: "+blinkmAddr+" - "+c);
  if( c == cEmpty ) c = Color.BLACK;  // empty is off
  if( !connected ) return;

  try { 
    if( arduinoMode ) {
      blinkmComm.fadeToRGB( blinkmAddr, c );
    } else { 
      linkm.fadeToRGB( blinkmAddr, c);  // FIXME:  which track 
    }
  } catch( IOException ioe) {        // hmm, what to do here
    connected = false;
  }
  return;
}

/**
 *
 */
public void sendBlinkMColors( int addrs[], Color colors[], int send_count ) {
  //l.debug("sendBlinkMColors "+send_count);
  if( !connected ) return;
  long st = System.currentTimeMillis();

  try { 
    for( int i=0; i<send_count; i++) {
      if( addrs[i]!=-1 ) {
        if( arduinoMode ) { 
          blinkmComm.fadeToRGB( addrs[i], colors[i] );
        } else { 
          linkm.fadeToRGB( addrs[i], colors[i] );
        }
      }
    }
    //linkm.fadeToRGB( addrs, colors, send_count );
  } catch( IOException ioe ) {
    connected = false;
    return;
  }
  long et = System.currentTimeMillis();
  if( debugLevel>2) l.debug("time to SendBlinkMColors: "+(et-st)+" millisecs");
  // FIXME: bad debug logic here
  return;
}

/**
 *
 */
public void prepareForPreview() {
  prepareForPreview(durationCurrent);
}

/**
 * Prepare blinkm for playing preview scripts
 * @param loopduration duration of loop in milli
 */
public void prepareForPreview(int loopduration) {
  byte fadespeed = getFadeSpeed(loopduration);
  l.debug("prepareForPreview: fadespeed:"+fadespeed);
  if( !connected ) return;
  //if( !connected ) connect(); // no, fails for case of unplug
  //if( checkForLinkM() ) connect(); // hmm gives weird no-playing failure modes

  int blinkmAddr = 0x00;  // FIXME: ????
  try { 
    if( arduinoMode ) { 
      blinkmComm.stopScript( blinkmAddr );
      blinkmComm.setFadeSpeed( blinkmAddr, fadespeed );
    } else { 
      linkm.stopScript( blinkmAddr );
      linkm.setFadeSpeed( blinkmAddr, fadespeed );
    }
  } catch(IOException ioe ) {
    // FIXME: hmm, what to do here
    l.debug("prepareForPreview: "+ioe);
    connected = false;
  }
}

/**
 * What happens when "download" button is pressed
 */
public boolean doDownload() {
  BlinkMScript script;
  BlinkMScriptLine scriptLine;
  Color c;
  int blinkmAddr;
  for( int j=0; j< numTracks; j++ ) {
    boolean active = multitrack.tracks[j].active;
    if( !active ) continue; 
    blinkmAddr = multitrack.tracks[j].blinkmaddr;
    try { 
      if( arduinoMode ) {
        script = blinkmComm.readScript( blinkmAddr, 0, true );
      } else {
        script = linkm.readScript( blinkmAddr, 0, true );  // read all
      }
      int len = (script.length() < numSlices) ? script.length() : numSlices;
      for( int i=0; i< len; i++) {
        scriptLine = script.get(i);
        // FIXME: maybe move this into BlinkMScriptLine
        if( scriptLine.cmd == 'c' ) {  // only pay attention to color cmds
          c = new Color( scriptLine.arg1,scriptLine.arg2,scriptLine.arg3 );
          //println("c:"+c+","+Color.BLACK); // FIXME: why isn't this equal?
          if( c == Color.BLACK ) { c = cEmpty; println("BLACK!"); }
          multitrack.tracks[j].slices[i] = c;
        }
      }
      multitrack.repaint();
    } catch( IOException ioe ) {
      l.error("doDownload: on track #"+j+",addr:"+blinkmAddr+"  "+ioe);
      connected = false;
    }
  }
  return true;
}

/**
 * What happens when "upload" button is pressed
 */
public boolean doUpload(JProgressBar progressbar) {
  if( !connected ) return false;
  multitrack.stop();
  boolean rc = false;

  int durticks = getDurTicks();
  int fadespeed = getFadeSpeed();
  int reps = (byte)((multitrack.looping) ? 0 : 1);  

  BlinkMScriptLine scriptLine;
  Color c;
  int blinkmAddr;

  for( int j=0; j<numTracks; j++ ) {
    if( ! multitrack.tracks[j].active ) continue;  // skip disabled tracks
    blinkmAddr = multitrack.tracks[j].blinkmaddr; // get track i2c addr
    
    try { 
      for( int i=0; i<numSlices; i++) {
        c =  multitrack.tracks[j].slices[i];         
        if( c == cEmpty )
          c = cBlack;
        
        scriptLine = new BlinkMScriptLine( durticks, 'c', c.getRed(),
                                           c.getGreen(),c.getBlue());
        if( arduinoMode ) {
          blinkmComm.writeScriptLine( blinkmAddr, i, scriptLine);
        } else { 
          linkm.writeScriptLine( blinkmAddr, i, scriptLine);
        }
        if( progressbar !=null) progressbar.setValue(i);  // hack
      }
      
      if( arduinoMode ) { 
        blinkmComm.setScriptLengthRepeats( blinkmAddr, numSlices, reps );
        blinkmComm.setStartupParams( blinkmAddr, 1, 0, 0, fadespeed, 0 );
        blinkmComm.setFadeSpeed( blinkmAddr, fadespeed);
      }
      else { 
        // set script length     cmd   id         length         reps
        linkm.setScriptLengthRepeats( blinkmAddr, numSlices, reps);
        // set boot params   addr, mode,id,reps,fadespeed,timeadj
        linkm.setStartupParams( blinkmAddr, 1, 0, 0, fadespeed, 0 );
        // set playback fadespeed
        linkm.setFadeSpeed( blinkmAddr, fadespeed);
      }
    } catch( IOException ioe ) { 
      l.error("upload error for blinkm addr "+blinkmAddr+ " : "+ioe);
    }
    
  } // for numTracks
  
  try { 
    // and play the script on all blinkms
    if( arduinoMode ) {
      blinkmComm.playScript( 0 );
    } else { 
      linkm.playScript( 0 );  // FIXME:  use LinkM to syncM
    }
    rc = true;
  } catch( IOException ioe ) { 
    l.error("upload error: "+ioe);
    rc = false;
    connected = false;
  }

  return rc;
}


/**
 * Open the edit chanel id and label dialog
 */
public void doTrackDialog(int track) {
  multitrack.reset(); // stop preview script
  
  setChannelDialog.setVisible(true);
  
  multitrack.reset();
  multitrack.repaint();

}


/**
 * Change the I2C address of the currently selected BlinkM
 */
public void doAddressChange() {
  int curraddr = multitrack.getCurrTrack().blinkmaddr;

  String question = 
    "Change address of current BlinkM \n"+
    "from address '"+curraddr+"' to :";
  String s = (String)JOptionPane.showInputDialog(mf, 
                                                 question, 
                                                 "BlinkM Readdressing",
                                                 JOptionPane.PLAIN_MESSAGE,
                                                 null,
                                                 null, new Integer(curraddr));
  if( s == null || s.length()==0 ) {  // no selection
    return;
  }
  int newaddr = Integer.parseInt(s);
  if( newaddr <= 0 && newaddr > 113 ) {  // bad value
    return;
  }

  try { 
    if( arduinoMode ) { 
      blinkmComm.setAddress( curraddr, newaddr );
    } else {
      linkm.setAddress( curraddr, newaddr ); 
    }
    multitrack.getCurrTrack().blinkmaddr = newaddr;
  } catch( IOException ioe ) {
    JOptionPane.showMessageDialog(mf,
                                  "Could not set BlinkM addres.\n"+ioe,
                                  "BlinkM Readdress failure",
                                  JOptionPane.WARNING_MESSAGE);
  }
  
}





// ----------------------------------------------------------------------------

/**
 * Load current track from a file.
 * Opens up a OpenDialog
 */
public void loadTrack() { 
  loadTrack( multitrack.currTrack );
}

/**
 * Loads specified file (if possible) into current track
 */
public void loadTrack(File file) {
  loadTrackWithFile( multitrack.currTrack, file );
}

/**
 * Load a text file containing a light script, turn it into BlinkMScriptLines
 * Opens up a OpenDialog then loads into track tracknum
 */
public void loadTrack(int tracknum) {
  int returnVal = fc.showOpenDialog(mf);  // this does most of the work
  if (returnVal != JFileChooser.APPROVE_OPTION) {
    return;
  }
  File file = fc.getSelectedFile();
  lastFile = file;
  loadTrackWithFile( tracknum, file );
}

/**
 *
 */
public void loadTrackWithFile(int tracknum, File file) {
  l.debug("loadTrackWithFile:"+tracknum+","+file);
  if( file != null ) {
    String[] lines = LinkM.loadFile( file );
    BlinkMScript script = LinkM.parseScript( lines );
    if( script == null ) {
      l.error("loadTrack: bad format in file");
      return;
    }
    script = script.trimComments(); 
    int len = script.length();
    if( len > numSlices ) {      // danger!
      len = numSlices;           // cut off so we don't overrun
    }
    int j=0;
    for( int i=0; i<len; i++ ) { 
      BlinkMScriptLine sl = script.get(i);
      if( sl.cmd == 'c' ) { // if color command
        Color c = new Color( sl.arg1, sl.arg2, sl.arg3 );
        multitrack.tracks[tracknum].slices[j++] = c;
      }
    }
    multitrack.repaint();  // hmm
  }
}

/**
 * Load all tracks from a file
 */
public void loadAllTracks() {
  multitrack.deselectAllTracks();
  fc.setSelectedFile(lastFile);
  int returnVal = fc.showOpenDialog(mf); 
  if (returnVal != JFileChooser.APPROVE_OPTION) {
    return;
  }
  File file = fc.getSelectedFile();
  lastFile = file;
  if( file != null ) {
    //LinkM.debug = 1;
    String[] lines = LinkM.loadFile( file );
    if( lines == null ) println(" null lines? ");
    BlinkMScript scripts[] = LinkM.parseScripts( lines );
    if( scripts == null ) {
      System.err.println("loadAllTracks: bad format in file");
      return;
    }

    for( int k=0; k<scripts.length; k++) { 
      BlinkMScript script = scripts[k];
      //println(i+":\n"+scripts[i].toString());
      script = script.trimComments(); 
      int len = script.length();
      if( len > numSlices ) {      // danger!
        len = numSlices;           // cut off so we don't overrun
      }
      int j=0;
      for( int i=0; i<len; i++ ) { 
        BlinkMScriptLine sl = script.get(i);
        if( sl.cmd == 'c' ) { // if color command
          Color c = new Color( sl.arg1, sl.arg2, sl.arg3 );
          multitrack.tracks[k].slices[j++] = c;
        }
      }
    }

  } //if(file!=null)
  multitrack.repaint();
}

/**
 * Save the current track to a file
 */
public void saveTrack() {
  saveTrack( multitrack.currTrack );
}

/**
 * Save a track to a file
 */
public void saveTrack(int tracknum) {
  if( lastFile!=null ) fc.setSelectedFile(lastFile);
  int returnVal = fc.showSaveDialog(mf);  // this does most of the work
  if( returnVal != JFileChooser.APPROVE_OPTION) {
    return;  // FIXME: need to deal with no .txt name no file saving
  }
  File file = fc.getSelectedFile();
  lastFile = file;
  if (file.getName().endsWith("txt") ||
      file.getName().endsWith("TXT")) {
    BlinkMScript script = new BlinkMScript();
    Color[] slices = multitrack.tracks[tracknum].slices;
    int durTicks = getDurTicks();
    for( int i=0; i< slices.length; i++) {
      Color c = slices[i];
      int r = c.getRed()  ;
      int g = c.getGreen();
      int b = c.getBlue() ;
      script.add( new BlinkMScriptLine( durTicks, 'c', r,g,b) );
    }    
    LinkM.saveFile( file, script.toString() );
  }
}

/**
 *
 */
public void saveAllTracks() {
  if( lastFile!=null ) fc.setSelectedFile(lastFile);
  int returnVal = fc.showSaveDialog(mf);  // this does most of the work
  if( returnVal != JFileChooser.APPROVE_OPTION) {
    return;  // FIXME: need to deal with no .txt name no file saving
  }
  File file = fc.getSelectedFile();
  // hack to make sure file always ends in .txt
  String fnameabs = file.getAbsolutePath();
  if( !(fnameabs.endsWith("txt") || fnameabs.endsWith("TXT")) ) {
    fnameabs = fnameabs + ".txt";
    file = new File( fnameabs );
  }

  lastFile = file;
  if (file.getName().endsWith("txt") ||
      file.getName().endsWith("TXT")) {

    StringBuffer sb = new StringBuffer();
    
    for( int k=0; k<numTracks; k++ ) {
      BlinkMScript script = new BlinkMScript();
      Color[] slices = multitrack.tracks[k].slices;
      int durTicks = getDurTicks();
      for( int i=0; i< slices.length; i++) {
        Color c = slices[i];
        int r = c.getRed()  ;
        int g = c.getGreen();
        int b = c.getBlue() ;
        script.add( new BlinkMScriptLine( durTicks, 'c', r,g,b) );
      }
      sb.append("{\n");
      sb.append( script.toString() );  // render track to string
      sb.append("}\n");
    }

    LinkM.saveFile( file, sb.toString() );  
  }
}

// ---------------------------------------------------------------------------

/**
 * Sets status label at bottom of mainframe
 */
public void setStatus(String status) {
    statusLabel.setText( status );
}
/**
 * Toggle a little dot on the bottom bar to indicate aliveness
 */
public void heartbeat() {
  String s = heartbeatLabel.getText();
  s = (s.equals(".")) ? " " : "."; // toggle
  heartbeatLabel.setText(s);
}

/**
 * Updates the current channel info at top of mainframe
 */
public void updateInfo() {
  //for( int i=0;
  Track trk = multitrack.tracks[multitrack.currTrack];
  currChanIdLabel.setText( String.valueOf(trk.blinkmaddr) );
  currChanLabel.setText( trk.label );
  multitrack.repaint();
  repaint();
}

/**
 * Creates all the GUI elements
 */
public void setupGUI() {

  setupMainframe();  // creates 'mf'

  Container mainpane = mf.getContentPane();
  BoxLayout layout = new BoxLayout( mainpane, BoxLayout.Y_AXIS);
  mainpane.setLayout(layout);

  JPanel chtop     = makeChannelsTopPanel();

  multitrack       = new MultiTrackView( mainWidth,300 );

  // controlsPanel contains colorpicker and all buttons
  JPanel controlsPanel = makeControlsPanel();
  JPanel bottomPanel = makeBottomPanel();

  // add everything to the main pane, in order
  mainpane.add( chtop );
  mainpane.add( multitrack );
  mainpane.add( controlsPanel );
  mainpane.add( bottomPanel );

  //mf.setVisible(true);
  mf.setResizable(false);

  fc = new JFileChooser( System.getProperty("user.home")  ); 
  fc.setFileFilter( new javax.swing.filechooser.FileFilter() {
      public boolean accept(File f) {
        if(f.isDirectory()) return true;
        if (f.getName().toLowerCase().endsWith("txt") ) return true;
        return false;
      }
      public String getDescription() { return "TXT files";  }
    }
    );
  
  setChannelDialog = new SetChannelDialog(); // defaults to invisible
  updateInfo();
}

/**
 * Make the panel that contains "CHANNELS" and the current channel info
 */
public JPanel makeChannelsTopPanel() {
  JPanel p = new JPanel();
  p.setBackground(cBgLightGray);  
  p.setLayout( new BoxLayout(p, BoxLayout.X_AXIS) );
  p.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

  ImageIcon chText = util.createImageIcon("blinkm_text_channels_fixed.gif",
                                          "CHANNELS");
  JLabel chLabel = new JLabel(chText);
  JLabel currChanIdText = new JLabel("CURRENT CHANNEL ID:");
  currChanIdText.setFont( textBigfont );
  currChanIdLabel = new JLabel("--");
  currChanIdLabel.setFont(textBigfont);
  JLabel currChanLabelText = new JLabel("LABEL:");
  currChanLabelText.setFont(textBigfont);
  currChanLabel = new JLabel("-nuh-");
  currChanLabel.setFont(textBigfont);

  p.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent me) {
        doTrackDialog(0);  // open up change track functionality
      }
    });

  p.add( Box.createRigidArea(new Dimension(25,0) ) );
  p.add(chLabel);
  p.add(Box.createHorizontalStrut(10));
  p.add(currChanIdText);
  p.add(Box.createHorizontalStrut(5));
  p.add(currChanIdLabel);
  p.add(Box.createHorizontalStrut(10));
  p.add(currChanLabelText);
  p.add(Box.createHorizontalStrut(5));
  p.add(currChanLabel);
  p.add(Box.createHorizontalGlue());  // boing

  return p;
}

/**
 * Make the controlsPanel that contains colorpicker and all buttons
 */
public JPanel makeControlsPanel() {
  JPanel colorChooserPanel = makeColorChooserPanel();
  buttonPanel       = new ButtonPanel(); //380, 280);

  JPanel controlsPanel = new JPanel();
  controlsPanel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
  //controlsPanel.setBorder(BorderFactory.createMatteBorder(10,0,0,0,cBgDarkGray));
  //controlsPanel.setBorder(BorderFactory.createCompoundBorder(  // debug
  //                 BorderFactory.createLineBorder(Color.blue),
  //                 controlsPanel.getBorder()));
  controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.X_AXIS));
  controlsPanel.add( colorChooserPanel );
  controlsPanel.add( buttonPanel );
  controlsPanel.add( Box.createHorizontalGlue() );
  return controlsPanel;
}

/**
 * Makes and sets up the colorChooserPanel
 */
public JPanel makeColorChooserPanel() { 
  FixedColorSelectionModel fixedModel = new FixedColorSelectionModel();

  colorChooser = new JColorChooser(fixedModel);

  colorChooser.setBackground(cBgDarkGray);
  colorChooser.getSelectionModel().addChangeListener( new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        Color c = colorChooser.getColor();
        multitrack.setSelectedColor(c);
      }
    });

  colorChooser.setPreviewPanel( new JPanel() ); // we have our custom preview
  colorChooser.setColor( cEmpty );

  JPanel colorChooserPanel = new JPanel();   // put it in its own panel for why?
  colorChooserPanel.setBackground(cBgDarkGray);  
  colorChooserPanel.add( Box.createVerticalStrut(5) );
  colorChooserPanel.add( colorChooser );

  colorChooser.addMouseListener( new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        println("CLICKY");
      }
    });
  return colorChooserPanel;
}

/**
 * Make the bottom panel, it contains version & copyright info and status line
 */
public JPanel makeBottomPanel() {
  JLabel versionLabel = new JLabel(versionInfo, JLabel.LEFT);
  statusLabel = new JLabel("status");
  heartbeatLabel = new JLabel(" ");
  versionLabel.setHorizontalAlignment(JLabel.LEFT);
  JPanel bp = new JPanel();
  bp.setBackground(cBgMidGray);
  bp.setLayout( new BoxLayout( bp, BoxLayout.X_AXIS) );
  bp.add( Box.createHorizontalStrut(10) );
  bp.add( versionLabel );
  bp.add( Box.createHorizontalGlue() );
  bp.add( heartbeatLabel );
  bp.add( Box.createHorizontalStrut(5) );
  bp.add( statusLabel );
  bp.add( Box.createHorizontalStrut(25) );
  return bp;
}

/**
 * Create the containing frame (or JDialog in this case) 
 */
public void setupMainframe() {
  mf = new JFrame( "BlinkM Sequencer" );
  mf.setBackground(cBgDarkGray);
  mf.setFocusable(true);
  mf.setSize( mainWidth, mainHeight);
  Frame f = mf;

  Toolkit tk = Toolkit.getDefaultToolkit();
  // FIXME: why doesn't either of these seem to work
  //ImageIcon i = new Util().createImageIcon("blinkm_thingm_logo.gif","title");
  //f.setIconImage(i.getImage());
  f.setIconImage(tk.getImage("blinkm_thingm_logo.gif"));

  // handle window close events
  mf.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        mf.dispose();          // close mainframe
        p.destroy();           // close processing window as well
        p.frame.setVisible(false); // hmm, seems out of order
        System.exit(0);
      }
    });
  
  // center MainFrame on the screen and show it
  Dimension scrnSize = tk.getScreenSize();
  mf.setLocation(scrnSize.width/2 - mf.getWidth()/2, 
                 scrnSize.height/2 - mf.getHeight()/2);
  setupMenus(f);
}


/**
 * The main menu and hotkey listener
 */
ActionListener menual = new ActionListener() { 
    public void actionPerformed(ActionEvent e) {
      String cmd = e.getActionCommand();
      l.debug("action listener: "+cmd);
      if(        cmd.equals("Quit") )  {
        System.exit(0);
      } else if( cmd.equals("Connect to Arduino") ) {
        blinkmComm.connectDialog();
        itemConnect.setLabel("Disconnect from Arduino");        
      } else if( cmd.equals("Disconnect from Arduino") ) {
        blinkmComm.disconnectDialog();
        itemConnect.setLabel("Connect to Arduino");
      } else if( cmd.equals("Load Set") ) {  // FIXME: such a hack
        loadAllTracks();
      } else if( cmd.equals("Save Set") ) { 
        saveAllTracks();
      } else if( cmd.equals("Load One Track") ) {
        loadTrack();
      } else if( cmd.equals("Save One Track") ) {
        saveTrack();
      } else if( cmd.equals("Cut") ) {
        multitrack.cut();
      } else if( cmd.equals("Copy") ) {
        multitrack.copy();
      } else if( cmd.equals("Paste") ) {
        multitrack.paste();
      } else if( cmd.equals("Delete") ) {
        multitrack.delete();
      } else if( cmd.equals("Select All in Track") ) {
        multitrack.selectAllinTrack();
      } else if( cmd.equals("Make Gradient") ) {
        multitrack.makeGradient();
      } else if( cmd.equals("Edit Channel IDs") ) {
        doTrackDialog(0);
      } else if( cmd.equals("Display LinkM/BlinkM Versions") ) {
        displayVersions();
      } else if( cmd.equals("Upgrade LinkM Firmware") ) {
        upgradeLinkMFirmware();
      } else if( cmd.equals("Reset LinkM") ) {
        resetLinkM();
      } else if( cmd.equals("BlinkM Factory Reset") ) {
        doFactoryReset();
      } else if( cmd.equals("Scan I2C Bus") ) {
        doI2CScan();
      } else if( cmd.equals("Change BlinkM I2C Address") ) {
        doAddressChange();
      } else if( cmd.equals("Help") ) {
        showHelp();
      } else if( cmd.equals("Quick Start Guide") ) {
        p.link("http://blog.thingm.com/2010/05/blinkm-hello-video-guides-example-code/", "_blank"); 
      } else if( cmd.startsWith("Script ") ) { // predef script
        int scriptnum = 0;
        String snum = cmd.substring("Script ".length(),cmd.indexOf(':'));
        scriptnum = Integer.parseInt(snum);
        setBootScript( scriptnum );
      } else {

      }
      multitrack.repaint();
    } // actionPerformed
  };


/**
 * Create all the application menus
 */
public void setupMenus(Frame f) {
  MenuBar menubar = new MenuBar();
  
  //create all the Menu Items and add the menuListener to check their state.
  Menu fileMenu = new Menu("File");
  Menu editMenu = new Menu("Edit");
  Menu toolMenu = new Menu("Tools");
  Menu helpMenu = new Menu("Help");

  MenuItem itemf1 = new MenuItem("Load Set", new MenuShortcut(KeyEvent.VK_O));
  MenuItem itemf2 = new MenuItem("Save Set", new MenuShortcut(KeyEvent.VK_S));
  MenuItem itemf2a= new MenuItem("-");
  MenuItem itemf3 = new MenuItem("Load One Track",
                                 new MenuShortcut(KeyEvent.VK_O, true));
  MenuItem itemf4 = new MenuItem("Save One Track",
                                 new MenuShortcut(KeyEvent.VK_S, true));
  MenuItem itemf4a= new MenuItem("-");

  itemConnect     = new MenuItem("Connect to Arduino", 
                                 new MenuShortcut(KeyEvent.VK_C,true));

  MenuItem itemf5a= new MenuItem("-");
  MenuItem itemf6 = new MenuItem("Quit", new MenuShortcut(KeyEvent.VK_Q));

  MenuItem iteme1= new MenuItem("Cut",  new MenuShortcut(KeyEvent.VK_X));
  MenuItem iteme2= new MenuItem("Copy", new MenuShortcut(KeyEvent.VK_C));
  MenuItem iteme3= new MenuItem("Paste",new MenuShortcut(KeyEvent.VK_V));
  MenuItem iteme4= new MenuItem("Delete",new MenuShortcut(KeyEvent.VK_D));
  MenuItem iteme4a=new MenuItem("-");
  MenuItem iteme5= new MenuItem("Select All in Track", new MenuShortcut(KeyEvent.VK_A));
  MenuItem iteme5a=new MenuItem("-");
  MenuItem iteme6= new MenuItem("Make Gradient", new MenuShortcut(KeyEvent.VK_G));
  MenuItem iteme6a=new MenuItem("-");
  MenuItem iteme7= new MenuItem("Edit Channel IDs");

  //MenuItem itemt2 = new MenuItem("Upgrade LinkM Firmware");
  MenuItem itemt1 = new MenuItem("BlinkM Factory Reset");
  Menu startupMenu = new Menu("Set BlinkM Startup Script to...");
  for( int i=0; i< startupScriptNames.length; i++) {
    MenuItem mi = new MenuItem(startupScriptNames[i]);
    mi.addActionListener(menual);
    startupMenu.add( mi );
  }
  MenuItem itemt2a= new MenuItem("-");
  MenuItem itemt3 = new MenuItem("Scan I2C Bus");
  MenuItem itemt4 = new MenuItem("Change BlinkM I2C Address");
  MenuItem itemt4a= new MenuItem("-");

  MenuItem itemt5 = new MenuItem("Display LinkM/BlinkM Versions");
  MenuItem itemt6 = new MenuItem("Reset LinkM");

  MenuItem itemh1 = new MenuItem("Help");
  MenuItem itemh2 = new MenuItem("Quick Start Guide");


  itemf1.addActionListener(menual);
  itemf2.addActionListener(menual);
  itemf3.addActionListener(menual);
  itemf4.addActionListener(menual);
  itemConnect.addActionListener(menual);
  itemf6.addActionListener(menual);
  iteme1.addActionListener(menual);
  iteme2.addActionListener(menual);
  iteme3.addActionListener(menual);
  iteme4.addActionListener(menual);
  iteme5.addActionListener(menual);
  iteme6.addActionListener(menual);
  iteme7.addActionListener(menual);
  itemt1.addActionListener(menual);
  //itemt2.addActionListener(menual);
  itemt3.addActionListener(menual);
  itemt4.addActionListener(menual);
  itemt5.addActionListener(menual);
  itemt6.addActionListener(menual);
  itemh1.addActionListener(menual);
  itemh2.addActionListener(menual);
  
  fileMenu.add(itemf1);
  fileMenu.add(itemf2);
  fileMenu.add(itemf2a);
  fileMenu.add(itemf3);
  fileMenu.add(itemf4);
  fileMenu.add(itemf4a);
  fileMenu.add(itemConnect);
  fileMenu.add(itemf5a);
  fileMenu.add(itemf6);
  
  editMenu.add(iteme1);
  editMenu.add(iteme2);
  editMenu.add(iteme3);
  editMenu.add(iteme4);
  editMenu.add(iteme4a);
  editMenu.add(iteme5);
  editMenu.add(iteme5a);
  editMenu.add(iteme6);
  editMenu.add(iteme6a);
  editMenu.add(iteme7);


  toolMenu.add(itemt1);
  toolMenu.add(startupMenu);
  //toolMenu.add(itemt2);
  toolMenu.add(itemt2a);
  toolMenu.add(itemt3);
  toolMenu.add(itemt4);
  toolMenu.add(itemt4a);
  toolMenu.add(itemt5);
  toolMenu.add(itemt6);

  helpMenu.add(itemh1);
  helpMenu.add(itemh2);

  menubar.add(fileMenu);
  menubar.add(editMenu);
  menubar.add(toolMenu);
  menubar.add(helpMenu);
  
  f.setMenuBar(menubar);   //add the menu to the frame
}

public byte getDurTicks() { 
  return getDurTicks(durationCurrent);
}

// uses global var 'durations'
public byte getDurTicks(int loopduration) {
  for( int i=0; i< timings.length; i++ ) {
    if( timings[i].duration == loopduration )
      return timings[i].durTicks;
  }
  return timings[0].durTicks; // failsafe
}

public byte getFadeSpeed() { 
  return getFadeSpeed(durationCurrent);
}

// this is so lame
public byte getFadeSpeed(int loopduration) {
  for( int i=0; i< timings.length; i++ ) {
    if( timings[i].duration == loopduration )
      return timings[i].fadeSpeed;
  }
  return timings[0].fadeSpeed; // failsafe
}

public void setDurationByIndex( int idx) {
  durationCurrent = timings[idx].duration;
}


/**
 * Bind keys to actions using a custom KeyEventPostProcessor
 * (fixme: why can't this go in the panel?)
 */
public void bindKeys() {
  // ahh, the succinctness of java
  KeyboardFocusManager kfm = 
    KeyboardFocusManager.getCurrentKeyboardFocusManager();
  
  kfm.addKeyEventDispatcher( new KeyEventDispatcher() {
      public boolean dispatchKeyEvent(KeyEvent e) {
        boolean rc = false;
        if( !mf.hasFocus() ) { 
          if( !multitrack.hasFocus() ) {
            return false;
          }
        }
        if(e.getID() != KeyEvent.KEY_PRESSED) 
          return false;
        int mod = e.getModifiers();
        //if(e.getModifiers() != 0)  // FIXME?
        //  return false;

        switch(e.getKeyCode()) {
        case KeyEvent.VK_UP:
          multitrack.prevTrack();  rc = true;
          break;
        case KeyEvent.VK_DOWN:
          multitrack.nextTrack();  rc = true;
          break;
        case KeyEvent.VK_LEFT:
          multitrack.prevSlice(mod);  rc = true;
          break;
        case KeyEvent.VK_RIGHT:
          multitrack.nextSlice(mod);  rc = true;
          break;
        case KeyEvent.VK_SPACE:
          if( multitrack.playing ) { 
            multitrack.stop();
          } else { 
            //verifyLinkM();  
            multitrack.play();
          }
          rc = true;
          break;
        case KeyEvent.VK_1:
          multitrack.changeTrack(1);  rc = true;
         break;
        case KeyEvent.VK_2:
          multitrack.changeTrack(2);  rc = true;
          break;
        case KeyEvent.VK_3:
          multitrack.changeTrack(3);  rc = true;
          break;
        case KeyEvent.VK_4:
          multitrack.changeTrack(4);  rc = true;
          break;
        case KeyEvent.VK_5:
          multitrack.changeTrack(5);  rc = true;
          break;
        case KeyEvent.VK_6:
          multitrack.changeTrack(6);  rc = true;
          break;
        case KeyEvent.VK_7:
          multitrack.changeTrack(7);  rc = true;
          break;
        case KeyEvent.VK_8:
          multitrack.changeTrack(8);  rc = true;
          break;
        }
        /*
          if(action!=null)
          getCurrentTab().actionPerformed(new ActionEvent(this,0,action));
        */
        return rc;
      }
    });
}



/**
 *
 */
/*
public boolean checkForLinkM() {  
  l.debug("checkForLinkM");
  if( connected ) {
    try { 
      linkm.getLinkMVersion();
    } catch(IOException ioe) {
      connected = false;
      linkm.close();
    }
    return true;
  }
  
  try { 
    linkm.open();
  } catch( IOException ioe ) {
    return false;
  }
  linkm.close();
  
  return true;
}
*/

/**
 * FIXME: this is unused.  superceded by checkForLinkM()/connect() interaction
 * Verifies connetion to LinkM and at least one BlinkM
 * Also clears out any I2C bus errors that may be present
 */
/*
public boolean verifyConnection() {
  try { 
    // FIXME: what to do here
    // 0. do i2c bus reset ?
    // 1. verify linkm connection          (need new cmd?)
    // 2. verify connection to 1st blinkm  (get version?)
    // 3. verify all blinkms?
    linkm.getVersion(0);
  } catch( IOException ioe ) {
  }
  return true;
}
*/

/* from processing/arduino

static public File getContentFile(String name) {
  String path = System.getProperty("user.dir");
  
  // Get a path to somewhere inside the .app folder
  if (isMacOS()) {
    //      <key>javaroot</key>
    //      <string>$JAVAROOT</string>
    String javaroot = System.getProperty("javaroot");
    if (javaroot != null) {
      path = javaroot;
    }
  }
  File working = new File(path);
  return new File(working, name);
}

**
 * returns true if Processing is running on a Mac OS X machine.
 *
static public boolean isMacOS() {
  return System.getProperty("os.name").indexOf("Mac") != -1;
}

**
 * returns true if running on windows.
 *
static public boolean isWindows() {
  return System.getProperty("os.name").indexOf("Windows") != -1;
}

**
 * true if running on linux.
 *
static public boolean isLinux() {
  return System.getProperty("os.name").indexOf("Linux") != -1;
}
*/

/*
--  OLD doTrackDialog --
    int blinkmAddr = tracks[track].blinkmaddr;
    String s = (String)
      JOptionPane.showInputDialog(
                                  this,
                                  "Enter a new BlinkM address for this track",
                                  "Set track address",
                                  JOptionPane.PLAIN_MESSAGE,
                                  null,
                                  null,
                                  ""+blinkmAddr);
    
    //If a string was returned, say so.
    if ((s != null) && (s.length() > 0)) {
      l.debug("s="+s);
      try { 
        blinkmAddr = Integer.parseInt(s);
        if( blinkmAddr >=0 && blinkmAddr < 127 ) { // i2c limits
          tracks[track].blinkmaddr = blinkmAddr;
        }
      } catch(Exception e) {}
      
    } 
    */

/*
 * BlinkMComm2.pde -- Talk to BlinkMs via Arduino+BlinkMCommunicator sketch
 *                    Very similar api to LinkM.java
 *
 * Note: uses global 'linkm' object for script parsing (bad tod)
 *
 * 2010 Tod E. Kurt, ThingM, http://thingm.com/
 *
 */




public class BlinkMComm2 {
  //public final boolean fakeIt = false;

  public String portName = null;
  public final int portSpeed = 19200;

  Serial port;

  //static public final int writePauseMillis = 15;
  static public final int writePauseMillis = 30;

  // Return a list of potential ports
  // they should be ordered by best to worst (but are not right now)
  // this can't be static as a .pde, sigh.
  public String[] listPorts() {
    String[] a = Serial.list();
    String osname = System.getProperty("os.name");
    if( osname.toLowerCase().startsWith("windows") ) {
      // reverse list because Arduino is almost always highest COM port
      for(int i=0;i<a.length/2;i++){
        String t = a[i]; a[i] = a[a.length-(1+i)]; a[a.length-(1+i)] = t;
      }
      //for(int left=0, int right=list.length-1; left<right; left++, right--) {
      //  // exchange the first and last
      //  String tmp = list[left]; list[left] = list[right]; list[right] = tmp;
      //}
    }
    if( debugLevel>0 ) { 
      for( int i=0;i<a.length;i++){
        println(i+":"+a[i]);
      }
    }
    return a;
  }

  public BlinkMComm2() {

  }

  
  /**
   * Connect to the given port
   * Can optionally take a PApplet (the sketch) to get serialEvents()
   * but this is not recommended
   *
   */
  public void connect( PApplet p, String portname ) throws Exception {
    l.debug("BlinkMComm.connect: portname:"+portname);
    try {
      if(port != null)
        port.stop(); 
      port = new Serial(p, portname, portSpeed);
      delay(100);
      
      // FIXME: check address, set it if needed

      arduinoMode = true;
      connected = true;
      //isConnected = true;
      portName = portname;
    }
    catch (Exception e) {
      arduinoMode = false;
      connected = false;
      //isConnected = false;
      portName = null;
      port = null;
      throw e;
    }
  }

  // disconnect but remember the name
  public void disconnect() {
    if( port!=null )
      port.stop();
    arduinoMode = false;
    connected = false;
  }

  /**
   * Send an I2C command to addr, via the BlinkMCommander Arduino sketch
   * Byte array must be correct length
   */
  public synchronized void sendCommand( byte addr, byte[] cmd ) {
    sendCommand( addr, cmd, 0 );
  }

  /**
   * Send a command and expect a response
   */
  public synchronized byte[] sendCommand( byte addr, byte[] cmd, int resplen ) {
    l.debug("BlinkMComm.sendCommand("+resplen+"):"+ (char)cmd[0]+ 
            ((cmd.length>1)?(","+(int)cmd[1]):"") + 
            ((cmd.length>3)?(","+(int)cmd[2]+","+(int)cmd[3]):""));

    port.clear();

    byte cmdfull[] = new byte[4+cmd.length];
    cmdfull[0] = 0x01;
    cmdfull[1] = addr;
    cmdfull[2] = (byte)cmd.length;
    cmdfull[3] = (byte)resplen;
    for( int i=0; i<cmd.length; i++) {
      cmdfull[4+i] = cmd[i];
    }
    port.write(cmdfull);

    if( resplen == 0 ) return null;  // no response so get out

    long start_time = millis();
    while( port.available() < resplen ) { // wait
      if( (millis() - start_time) > 1000 ) { 
        return null; // FIXME: better error handling
      }
    }
    
    byte[] respbuf = new byte[resplen];
    port.readBytes(respbuf);
    
    return respbuf;
  }

  /**
   *
   */
  public byte[] i2cScan( int startAddr, int endAddr ) {
    byte[] cmd = {(byte)startAddr, (byte)endAddr}; 

    sendCommand( (byte)128, cmd); // 128 == i2cscan cmd
    delay(100);

    byte[] buh = new byte[endAddr-startAddr];
    int i=0;

    long start_time = millis();
    while( port.available() > 0 ) { // wait
      if( (millis() - start_time) > 1000 ) { 
        return buh; // FIXME: better error handling
      }
      byte addr   = (byte)port.read();
      byte result = (byte)port.read();
      if( result==0 ) {
        buh[i++] = addr;
      }
    }
    byte[] duh = new byte[i];
    System.arraycopy( buh, 0, duh, 0, i);
     
    return duh;
  }

  /**
   *
   */
  public void stopScript( int addr ) {
    byte[] cmd = {'o'};
    sendCommand( (byte)addr, cmd );
  }

  public void setFadeSpeed( int addr, int fadespeed ) {
    byte[] cmd = {'f', (byte)fadespeed};
    sendCommand( (byte)addr, cmd );
  }

  /**
   * Set the blinkm at 'addr' to the specified RGB color 
   *
   * @param addr the i2c address of blinkm
   * @param r red component, 8-bit
   * @param g green component, 8-bit
   * @param b blue component, 8-bit
   */
  public void setRGB(int addr, int r, int g, int b) {
    byte[] cmd = { 'n', (byte)r, (byte)g, (byte)b };
    sendCommand( (byte)addr, cmd );
  }

  public void setRGB( int addr, Color c ) {
    byte[] cmd = {'n', (byte)c.getRed(),(byte)c.getGreen(),(byte)c.getBlue() };
    sendCommand( (byte)addr, cmd );
  }

  public void fadeToRGB( int addr, Color c ) {
    byte[] cmd = {'c', (byte)c.getRed(),(byte)c.getGreen(),(byte)c.getBlue() };
    sendCommand( (byte)addr, cmd );
  }

  /**
   * Turn BlinkM at address addr off.
   * @param addr the i2c address of blinkm
   */
  public void off(int addr) {
    stopScript(addr);
    setRGB(addr, 0,0,0 );
  }

  /**
   * Get the version of a BlinkM at a specific address
   * @param addr the i2c address
   * @returns 2 bytes of version info
   */
  public byte[] getVersion(int addr) {
    byte[] cmd = { 'Z' };
    byte[] respbuf = sendCommand( (byte)addr, cmd, 2);
    return respbuf;
  }

  /**
   * Sets the I2C address of a BlinkM
   * @param addr old address, can be 0 to change all connected BlinkMs
   * @param newaddr new address
   //* @throws IOException on transmit or receive error
   */
  public void setAddress(int addr, int newaddr) {
    byte[] cmd = { (byte)'A', (byte)newaddr, 
                   (byte)0xD0, (byte)0x0D, (byte)newaddr };
    sendCommand( (byte)addr, cmd );
  }

  /**
   * Play a light script
   * @param addr the i2c address
   * @param script_id id of light script (#0 is reprogrammable one)
   * @param reps  number of repeats
   * @param pos   position in script to play
   //* @throws IOException on transmit or receive error
   */
  public void playScript(int addr, int script_id, int reps, int pos) {
    byte[] cmd = { 'p', (byte)script_id, (byte)reps, (byte)pos};
    sendCommand( (byte)addr, cmd );
  }
  /**
   * Plays the eeprom script (script id 0) from start, forever
   * @param addr the i2c address of blinkm
   //* @throws IOException on transmit or receive error
   */
  public void playScript(int addr) {
    playScript(addr, 0,0,0);
  }

  /**
   *
   */
  public void writeScriptLine( int addr, int pos, BlinkMScriptLine line ) {
    l.debug("writeScriptLine: addr:"+addr+" pos:"+pos+" scriptline: "+line);
    // build up the byte array to send
    byte[] cmd = new byte[8];    // 
    cmd[0] = (byte)'W';          // "Write Script Line" command
    cmd[1] = (byte) 0;           // script id (0==eeprom)
    cmd[2] = (byte)pos;          // script line number
    cmd[3] = (byte)line.dur;     // duration in ticks
    cmd[4] = (byte)line.cmd;     // command
    cmd[5] = (byte)line.arg1;    // cmd arg1
    cmd[6] = (byte)line.arg2;    // cmd arg2
    cmd[7] = (byte)line.arg3;    // cmd arg3
    
    sendCommand( (byte)addr, cmd );
    delay( writePauseMillis );// enforce at >4.5msec delay between EEPROM writes
  }

  /**
   * Write an entire light script contained in a string
   * @param addr the i2c address of blinkm
   */
  public void writeScript( int addr, String scriptstr ) {
    BlinkMScript script = linkm.parseScript( scriptstr );
    writeScript( addr, script );
  }

  /**
   * Write an entire BlinkM light script as a BlinkMScript
   * to blinkm at address 'addr'.
   * NOTE: for a 48-line script, this takes about 858 msecs because of 
   *       enforced 10 msec delay and HID overhead from small report size
   * FIXME: speed this up by implementing second report size 
   * @param addr the i2c address of blinkm
   * @param script BlinkMScript object of script lines
   */
  public void writeScript( int addr,  BlinkMScript script) {
    int len = script.length();
    
    for( int i=0; i< len; i++ ) {
      writeScriptLine( addr, i, script.get(i) );
    }
    
    setScriptLengthRepeats( addr, len, 0);    
  }


  /**
   * Set boot params   cmd,mode,id,reps,fadespeed,timeadj
   * @param addr the i2c address of blinkm
   //* @throws IOException on transmit or receive error
   */
  public void setStartupParams( int addr, int mode, int script_id, int reps, 
                                int fadespeed, int timeadj ) {
    byte[] cmd = { 'B', (byte)mode, (byte)script_id, 
                   (byte)reps, (byte)fadespeed, (byte)timeadj };
    sendCommand( (byte)addr, cmd );
    delay( writePauseMillis );  // enforce wait for EEPROM write
  }

  /**
   * Default values for startup params
   * @param addr the i2c address of blinkm
   //* @throws IOException on transmit or receive error
   */
  public void setStartupParamsDefault(int addr) {
    setStartupParams( addr, 1, 0, 0, 8, 0 );
  }

  /**
   * Set light script default length and repeats.
   * reps == 0 means infinite repeats
   * @param addr the i2c address of blinkm
   */
  public void setScriptLengthRepeats( int addr, int len, int reps) {
    byte[] cmd = { 'L', 0, (byte)len, (byte)reps };
    sendCommand( (byte)addr, cmd );
    delay( writePauseMillis );  // enforce wait for EEPROM write
  }


  /**
   * Read a BlinkMScriptLine from 'script_id' and pos 'pos', 
   * from BlinkM at 'addr'.
   * @param addr the i2c address of blinkm
   */
  public BlinkMScriptLine readScriptLine( int addr, int script_id, int pos ) {
    l.debug("readScriptLine: addr: "+addr+" pos:"+pos);
    byte[] cmd = new byte[3];     // 
    cmd[0] = (byte)'R';           // "Write Script Line" command
    cmd[1] = (byte)script_id;     // script id (0==eeprom)
    cmd[2] = (byte)pos;           // script line number

    byte[] respbuf = sendCommand( (byte)addr, cmd, 5);
    
    BlinkMScriptLine line = new BlinkMScriptLine();
    if( !line.fromByteArray(respbuf) ) return null;
    return line;  // we're bad
  }

  /**
   * Read an entire light script from a BlinkM at address 'addr' 
   * FIXME: this only really works for script_id==0
   * @param addr the i2c address of blinkm
   * @param script_id id of script to read from (usually 0)
   * @param readAll read all script lines, or just the good ones
   * @throws IOException on transmit or receive error
   */
  public BlinkMScript readScript( int addr, int script_id, boolean readAll ) 
    throws IOException { 
    BlinkMScript script = new BlinkMScript();
    BlinkMScriptLine line;
    for( int i = 0; i< maxScriptLength; i++ ) {
      line = readScriptLine( addr, script_id, i );
      if( line==null 
          || (line.cmd == 0xff && line.dur == 0xff) //(null or -1,-1 == bad loc 
          || (line.cmd == 0x00 && !readAll)
          ) { 
        return script;
        // ooo bad bad scriptline 
      } else { 
        script.add(line);
      }
    }
    return script;
  }


  /**
   * Set a BlinkM back to factory settings
   * Sets the i2c address to 0x09
   * Writes a new light script and sets the startup paramters
   * @param addr the i2c address of blinkm
   * @throws IOException on transmit or receive error
   */
  public void doFactoryReset( int addr ) throws IOException {
    setAddress( addr, 0x09 );
    addr = 0x09;

    setStartupParamsDefault(addr);

    BlinkMScript script = new BlinkMScript();
    script.add( new BlinkMScriptLine(  1, 'f',   10,   0,   0) );
    script.add( new BlinkMScriptLine(100, 'c', 0xff,0xff,0xff) );
    script.add( new BlinkMScriptLine( 50, 'c', 0xff,0x00,0x00) );
    script.add( new BlinkMScriptLine( 50, 'c', 0x00,0xff,0x00) );
    script.add( new BlinkMScriptLine( 50, 'c', 0x00,0x00,0xff) );
    script.add( new BlinkMScriptLine( 50, 'c', 0x00,0x00,0x00) );
    for( int i=0; i< 49-6; i++ ) {  // FIXME:  make this length correct
      script.add( new BlinkMScriptLine( 0, 'c', 0,0,0 ) );
    }

    writeScript( addr, script);

  }

  // ------------------------------------------------------------------------

  /**
   * What happens when "upload" button is pressed
   */
  public boolean doUpload(JProgressBar progressbar) {
    return false;
  }



  JDialog connectDialog;
  JComboBox portChoices;

  public void connectDialog() {

    String[] portNames = listPorts();
    String lastPortName = portName;
    
    if( lastPortName == null ) 
      lastPortName = (portNames.length!=0) ? portNames[0] : null;

    // FIXME: need to catch case of *no* serial ports (setSelectedIndex fails)
    int idx = 0;
    for( int i=0; i<portNames.length; i++) 
      if( portNames[i].equals(lastPortName) ) idx = i;

    portChoices = new JComboBox(portNames);
    portChoices.setSelectedIndex( idx );

    connectDialog = new JDialog();
    connectDialog.setTitle("Connect to Arduino");
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder( BorderFactory.createEmptyBorder(20,20,20,20) );

    JButton connectButton = new JButton("Connect");
    connectButton.addActionListener( new ActionListener() { 
        public void actionPerformed(ActionEvent e) {
          String portname = (String) portChoices.getSelectedItem();
          try { 
            connect(p, portname );

            delay(1500); // FIXME: wait for diecimila

            stopScript( 0 );
            setRGB( 0, Color.BLACK );

          } 
          catch( Exception ex ) {
            ex.printStackTrace(); //l.debug(ex);
            JOptionPane.showMessageDialog(mf, 
                                          "Couldn't open port "+portname+".",
                                          "Connect to Arduino Failed",
                                          JOptionPane.INFORMATION_MESSAGE);
          }
          connectDialog.setVisible(false);
        }
      });

    JPanel chooserPanel = new JPanel();
    chooserPanel.add(portChoices);
    chooserPanel.add(connectButton);

    JLabel msgtop = new JLabel("Please select serial port of Arduino with BlinkMCommunicator");
    panel.add( msgtop, BorderLayout.NORTH );
    panel.add( chooserPanel, BorderLayout.CENTER );

    connectDialog.getContentPane().add(panel); // jdialog has limited container 
    connectDialog.pack();
    connectDialog.setResizable(false);
    connectDialog.setLocationRelativeTo(null); // center it on BlinkMSequencer
    connectDialog.setVisible(true);

    connectDialog.addWindowListener( new WindowAdapter() {
        public void windowDeactivated(WindowEvent event) {
            // need to do anything here?
        }
      });
  }

  public void disconnectDialog() { 
    disconnect();
    JOptionPane.showMessageDialog(mf, 
                                  "Disconnected from "+portName+".",
                                  "Disconnect from Arduino",
                                  JOptionPane.INFORMATION_MESSAGE);
  }

}
// Copyright (c) 2007-2008, ThingM Corporation

/**
 *
 */
public class BurnDialog extends JDialog implements ActionListener {

  private String msg_uploading = "Uploading...";
  private String msg_done = "Done";
  private String msg_nowplaying = "Now playing script...";
  private String msg_error = "ERROR: not connected to a BlinkM.";
  private String msg_empty = "     ";

  private JLabel msgtop;
  private JLabel msgbot;
  private JProgressBar progressbar;
  private JButton okbut;

  private JButton burnBtn;

  public BurnDialog(JButton aBurnBtn) {
    //super(owner, "BlinkM Connect",true);  // modal
    super();
    burnBtn = aBurnBtn;
    burnBtn.setEnabled(false);
    
    setTitle("BlinkM Upload");

    JPanel panel = new JPanel(new GridLayout(0,1));
    panel.setBorder( BorderFactory.createEmptyBorder(20,20,20,20) );

    msgtop = new JLabel(msg_uploading);
    progressbar = new JProgressBar(0, numSlices-1);
    msgbot = new JLabel(msg_nowplaying);
    msgbot.setVisible(false);
    okbut = new JButton("Ok");
    okbut.setVisible(false);
    okbut.addActionListener(this);

    panel.add( msgtop );
    panel.add( progressbar );
    panel.add( msgbot );
    panel.add( okbut );
    getContentPane().add(panel);

    pack();
    setResizable(false);
    setLocationRelativeTo(null); // center it on the BlinkMSequencer
    setVisible(true);
    
    multitrack.reset(); // stop preview script
    buttonPanel.setToPlay();  // reset play button

    // so dumb we have to spawn a thread for this
    new Thread( new Burner() ).start();

  }
  // when the burn button is pressed
  public void actionPerformed(ActionEvent e) {
    burnBtn.setEnabled(true);  // seems like such a hack  (why did i do this?)
    prepareForPreview();
    setVisible(false);
  }
      
  public void isDone() {
    msgbot.setVisible(true);
    okbut.setVisible(true);
  }

  // FIXME: On this whole thing, it's a mess
  class Burner implements Runnable {
    public void run() {

      msgtop.setText( msg_uploading );
    
      boolean rc = doUpload(progressbar);

      if( rc == true ) 
          msgtop.setText( msg_uploading + msg_done );
      else 
          msgtop.setText( msg_error );

      msgbot.setText( msg_nowplaying );
      
      isDone();
    } // run
  }
}


// Copyright (c) 2007-2008, ThingM Corporation

/**
 * ButtonPanel contains all the main control buttons: play, burn (upload), etc.
 */
public class ButtonPanel extends JPanel {

  JButton playBtn;
  JButton downloadBtn, uploadBtn;
  JButton openBtn, saveBtn;
  JComboBox durChoice;

  private ImageIcon iconPlay;
  private ImageIcon iconPlayHov;
  private ImageIcon iconStop;
  private ImageIcon iconStopHov;
  
  /**
   *
   */
  public ButtonPanel() { //int aWidth, int aHeight) {
    //setPreferredSize(new Dimension(aWidth,aHeight));
    //setMaximumSize(new Dimension(aWidth,aHeight));

    //setBorder(BorderFactory.createCompoundBorder(  // debug
    //BorderFactory.createLineBorder(Color.red),this.getBorder()));

    // add play button
    makePlayButton();

    // add download button
    downloadBtn = new Util().makeButton("blinkm_butn_download_normal.gif",
                                        "blinkm_butn_download_hover.gif",
                                        "Download from BlinkMs", cBgDarkGray);
    downloadBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          doDownload();
        }
      });

    // add upload button
    uploadBtn = new Util().makeButton("blinkm_butn_upload_normal.gif",
                                      "blinkm_butn_upload_hover.gif",
                                      "Upload to BlinkMs", cBgDarkGray);
    uploadBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if( !connected ) return;
          new BurnDialog(uploadBtn);
        }
      });
    
    // add open button
    openBtn = new Util().makeButton("blinkm_butn_open_normal.gif",
                                    "blinkm_butn_open_hover.gif",
                                    "Open Sequences File", cBgDarkGray);
    openBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
            loadAllTracks();
        }
      });
    
    // add save button
    saveBtn = new Util().makeButton("blinkm_butn_save_normal.gif",
                                    "blinkm_butn_save_hover.gif",
                                    "Save Sequences File", cBgDarkGray);
    saveBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
            saveAllTracks();
        }
      });
    

    JPanel loopPanel = makeLoopControlsPanel();

    JPanel playBtnPanel = new JPanel();
    playBtnPanel.setBackground(cBgDarkGray);
    playBtnPanel.add( playBtn);

    JPanel updnPanel = new JPanel(); // new FlowLayout(FlowLayout.LEFT,0,0) );
    updnPanel.setBackground(cBgDarkGray);
    updnPanel.add(downloadBtn);
    updnPanel.add(uploadBtn);
    //updnPanel.setBorder(BorderFactory.createCompoundBorder(  // debug
    //BorderFactory.createLineBorder(Color.red),updnPanel.getBorder()));

    JPanel opensavePanel = new JPanel();
    opensavePanel.setBackground(cBgDarkGray);
    opensavePanel.add(openBtn);
    opensavePanel.add(saveBtn);

    JPanel grayspacePanel = new JPanel();
    grayspacePanel.setBackground(cBgMidGray);
    //grayspacePanel.add(Box.createVerticalStrut(5)) );

    this.setBackground(cBgDarkGray);
    this.setLayout( new BoxLayout(this, BoxLayout.Y_AXIS));
    this.add(loopPanel);
    this.add(Box.createVerticalStrut(4));
    this.add(playBtnPanel); 
    this.add(grayspacePanel); //Box.createVerticalStrut(5));
    this.add(updnPanel);
    //this.add(Box.createVerticalStrut(1));
    this.add(opensavePanel);

  }

  /**
   *
   */
  public JPanel makeLoopControlsPanel() {
    // add Loop Check Box
    //ImageIcon loopCheckIcn= new Util().createImageIcon("blinkm_text_loop.gif",
    //                                                    "Loop");
    //JLabel loopCheckLabel = new JLabel(loopCheckIcn);
    JLabel loopCheckLabel = new JLabel("LOOP");
    loopCheckLabel.setFont(textBigfont);
    loopCheckLabel.setForeground(cBgMidGray);

    JCheckBox loopCheckbox = new JCheckBox("", true);
    loopCheckbox.setBackground(cBgMidGray);
    ActionListener actionListener = new ActionListener() {
        public void actionPerformed(ActionEvent actionEvent) {
          AbstractButton abButton = (AbstractButton) actionEvent.getSource();
          boolean looping = abButton.getModel().isSelected();
          multitrack.looping = looping;
        }
      };
    loopCheckbox.addActionListener(actionListener);
    
    // add Loop speed label
    //ImageIcon loopIcn=new Util().createImageIcon("blinkm_text_loop_speed.gif",
    //                                               "Loop Speed");
    JLabel loopLabel = new JLabel("LOOP SPEED");
    loopLabel.setFont(textBigfont);
    loopLabel.setForeground(cBgMidGray);

    durChoice = new JComboBox();
    for( int i=0; i< timings.length; i++ ) {
        durChoice.addItem( timings[i].duration+ " seconds");  
    }

    // action listener for duration choice pull down
    durChoice.setBackground(cBgMidGray);
    //durChoice.setForeground(cBgMidGray);
    durChoice.setMaximumSize( durChoice.getPreferredSize() ); 
    durChoice.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent ie) {
          if( ie.getStateChange() == ItemEvent.SELECTED ) {
            int idx = durChoice.getSelectedIndex();  // FIXME
            setDurationByIndex(idx); //durationCurrent = timings[indx].duration;
            prepareForPreview();//durationCurrent);
          }
        }        
      }
      );
    
    JPanel loopPanel = new JPanel();
    loopPanel.setLayout(new BoxLayout( loopPanel, BoxLayout.X_AXIS) );
    loopPanel.setBackground(cBgDarkGray);
    //loopPanel.add(Box.createHorizontalGlue());
    loopPanel.add(Box.createHorizontalStrut(108));
    loopPanel.add(loopCheckLabel);
    loopPanel.add(Box.createHorizontalStrut(5));
    loopPanel.add(loopCheckbox);
    loopPanel.add(Box.createHorizontalStrut(10));
    loopPanel.add(loopLabel);
    loopPanel.add(Box.createHorizontalStrut(5));
    loopPanel.add(durChoice);
    //loopPanel.add(Box.createHorizontalStrut(38));

    return loopPanel;
  }


  /**
   *
   */
  public void makePlayButton() { 
    
    iconPlay    = new Util().createImageIcon("blinkm_butn_play_normal.gif", 
                                             "Play"); 
    iconPlayHov = new Util().createImageIcon("blinkm_butn_play_hover.gif", 
                                             "Play"); 
    // FIXME FIXME FIXME: need blinkm_butn_stop_{normal,hover}.gif
    iconStop    = new Util().createImageIcon("blinkm_butn_stop_normal.gif",  
                                             "Stop"); 
    iconStopHov = new Util().createImageIcon("blinkm_butn_stop_hover.gif", 
                                             "Stop"); 
    playBtn = new JButton();
    playBtn.setOpaque(true);
    playBtn.setBorderPainted( false );
    playBtn.setBackground(cBgDarkGray);
    playBtn.setRolloverEnabled(true);
    setPlayIcon();

    playBtn.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          // if we are going from not playing to playing, start timeline
          if( !multitrack.playing ) {
            // stop playing uploaded script, prep for preview playing
            prepareForPreview();
            multitrack.play();
          }
          else {
            multitrack.reset();
          }
          
          //isPlaying = !isPlaying;
          l.debug("Playing: " + multitrack.playing);
          setPlayIcon();

          multitrack.deselectAllTracks();  // hmmm.

        }
      });
  }

  /**
   *
   */
  public void setPlayIcon() {
    if( multitrack.playing ) {
      playBtn.setIcon(iconStop);
      playBtn.setRolloverIcon(iconStopHov); 
    } 
    else {
      playBtn.setIcon(iconPlay);
      playBtn.setRolloverIcon(iconPlayHov); 
    } 
  }

  /**
   *
   */
  public void setToPlay() {
    playBtn.setIcon(iconPlay);
    playBtn.setRolloverIcon(iconPlayHov); 
    //isPlaying = false;
  }

  public void enableButtons(boolean b) {
    if( b ) {
      uploadBtn.setEnabled(true);
      downloadBtn.setEnabled(true);
    }
    else {
      uploadBtn.setEnabled(false);
      downloadBtn.setEnabled(false);
    }
  }

}

/*
 *
 */





//import java.io.Serializable;

/**
 * Fixed from the DefaultColorSelectionModel
 * @see java.awt.Color
 */
public class FixedColorSelectionModel implements ColorSelectionModel {

    /**
     * Only one <code>ChangeEvent</code> is needed per model instance
     * since the event's only (read-only) state is the source property.
     * The source of events generated here is always "this".
     */
    protected transient ChangeEvent changeEvent = null;

    protected EventListenerList listenerList = new EventListenerList();

    private Color selectedColor;

    /**
     * the default constructor.
     */
    public FixedColorSelectionModel() {
        selectedColor = Color.white;
    }

    /**
     * @param color the new <code>Color</code>
     */
    public FixedColorSelectionModel(Color c) {
        selectedColor = c;
    }

    /**
     * Returns the selected <code>Color</code> which should be
     * non-<code>null</code>.
     *
     * @return the selected <code>Color</code>
     */
    public Color getSelectedColor() {
        return selectedColor;
    }

    /**
     * Sets the selected color to <code>color</code>.
     * Note that setting the color to <code>null</code> 
     * is undefined and may have unpredictable results.
     * This method fires a state changed event if it sets the
     * current color to a new non-<code>null</code> color;
     *
     * This is the method that was fixed --tod
     *
     * @param color the new <code>Color</code>
     */
    public void setSelectedColor(Color c) {
      if (c != null ) {// && !selectedColor.equals(color)) {
            selectedColor = c;
            fireStateChanged();
        }
    }


    /**
     * Adds a <code>ChangeListener</code> to the model.
     *
     * @param l the <code>ChangeListener</code> to be added
     */
    public void addChangeListener(ChangeListener l) {
	listenerList.add(ChangeListener.class, l);
    }

    /**
     * Removes a <code>ChangeListener</code> from the model.
     * @param l the <code>ChangeListener</code> to be removed
     */
    public void removeChangeListener(ChangeListener l) {
	listenerList.remove(ChangeListener.class, l);
    }

    /**
     * Returns an array of all the <code>ChangeListener</code>s added
     * to this <code>DefaultColorSelectionModel</code> with
     * <code>addChangeListener</code>.
     *
     * @return all of the <code>ChangeListener</code>s added, or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public ChangeListener[] getChangeListeners() {
        return (ChangeListener[])listenerList.getListeners(
                ChangeListener.class);
    }

    /**
     * Runs each <code>ChangeListener</code>'s
     * <code>stateChanged</code> method.
     *
     * <!-- @see #setRangeProperties    //bad link-->
     * @see EventListenerList
     */
    protected void fireStateChanged()
    {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -=2 ) {
            if (listeners[i] == ChangeListener.class) {
                if (changeEvent == null) {
                    changeEvent = new ChangeEvent(this);
                }
                ((ChangeListener)listeners[i+1]).stateChanged(changeEvent);
            }
        }
    }

}


/*
 *
 */
public class GridTestDialog extends JDialog implements MouseListener {

  JLabel[][] trackCells = new JLabel[numTracks][numSlices];
  HashMap cellsToIJ = new HashMap();

  Border bblk = BorderFactory.createLineBorder(Color.black);
  Border bred = BorderFactory.createLineBorder(Color.red);

  boolean mousedown = false;
  int currTrack = 0;

  public GridTestDialog() {
    super();

    JPanel trackpanel = new JPanel();
    trackpanel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
    trackpanel.setLayout( new GridLayout( numTracks,numSlices ) );

    for( int i=0; i<numTracks; i++ ) {
      Track track = multitrack.tracks[i];
      for( int j=0; j<numSlices; j++) {
        Color c = track.slices[j];
        JLabel l = new JLabel(i+","+j);
        l.setBorder(bblk);
        l.setBackground( cBgDarkGray );
        l.setPreferredSize( new Dimension(18,18) );
        l.addMouseListener(this);
        trackCells[i][j] = l;
        trackpanel.add( l );
        cellsToIJ.put( l, new Point( j,i) );  // FIXME: really? this is what we do?
      }
    }

    getContentPane().add(trackpanel);
    pack();
    setResizable(false);
    setLocationRelativeTo(null); // center it on the BlinkMSequencer
    super.setVisible(false);

    setTitle("Grid Test");

  }

  // Invoked when the mouse button has been clicked (pressed & released)
  public void mouseClicked(MouseEvent e) {
    //println("mouseClicked");
    
  }
  // Invoked when the mouse enters a component.
  public void mouseEntered(MouseEvent e) {
    //println("mouseEntered");
    // if we're mousedown and in same row, select
    if( mousedown ) {
      JLabel l = (JLabel)e.getComponent() ;
      Point p = (Point)cellsToIJ.get( l );
      println("mouseEnered: "+p);
      if( p.y == currTrack ) {
        selectOn( l );
      }
    }
  }
  // Invoked when the mouse exits a component.
  public void mouseExited(MouseEvent e) {
    //println("mouseExited");
  }
  // Invoked when a mouse button has been pressed on a component.
  public void	mousePressed(MouseEvent e) {
    JLabel l = (JLabel)e.getComponent();
    selectOn(l);
    mousedown = true;
    Point p = (Point)cellsToIJ.get( l );
    currTrack = p.y;
    println("mousePressed: "+currTrack);
    // begin select, say "mousedown!" and set what row we're in
  }
  public void 	mouseReleased(MouseEvent e)  {
    println("mouseReleased");
    // if in mousedown, end select
    mousedown = false;
  }

  public void selectOn(JLabel l ) {
    l.setBorder(bred);
  }

}

// Copyright (c) 2007-2008, ThingM Corporation

/**
 *
 */
class Log {
  int level = 0;

  public Log() {
    info("Log started");
  }  
  public void setLevel(int l) {
    level = l;
  }

  // shortcut call to debug() method
  public void d(Object o) {
    debug(o);
  }  

  public void debug(Object o) {
    if(level>0) println("DEBUG: " + o.toString());
  }

  public void info(Object o) {
    println("INFO:  " + o.toString());
  }

  public void warn(Object o) {
    println("WARN:  " + o.toString());
  }

  // shortcut call to error() method
  public void err(Object o) {
    error(o);
  }

  public void error(Object o) {
    println("ERROR:  " + o.toString());
  }

}
/**
 * MultiTrackView
 *
 * Owns one or more Tracks
 * Draws scrubber area and playhead
 * Handles movement playhead (by mouse or by timing)
 * Handles selection of slices in tracks
 * Interrogates Tracks for their state .. DUH
 * Handles enable & address buttons on side of each track
 *
 */

public class MultiTrackView
  extends JPanel implements MouseListener, MouseMotionListener {

  PImage previewAlpha;  // oi
  PImage checkboxImg;

  Track[] tracks;
  Color[] previewColors;
  int previewFadespeed = 25;
  Track bufferTrack;  // for copy-paste ops

  int currTrack;                            // currently selected track
  int currSlice;                            // only valid on playback

  boolean playing = false;                  //    
  boolean looping = true;                   // loop or single-shot

  private int scrubHeight = 12;             // height of scrubber area
  private int spacerWidth = 2;              // width between cells
  private int spacerHalf = spacerWidth/2;
  private int w,h;                          // dimensions of me
  private int sx = 57;                    // aka "trackX", offset from left edge
  private int previewWidth = 19;            // width of preview cells
  private int sliceWidth   = 17;            // width of editable cells
  private int trackHeight  = 18;            // height of each track 
  private int trackWidth;                   // == numSlices * sliceWidth
  private int previewX;
  private Color playHeadC = new Color(255, 0, 0);
  private float playHeadCurr;
  private boolean playheadClicked = false;

  private Font trackfont;

  private Point mouseClickedPt;
  private Point mousePt = new Point();;

  private long startTime = 0;             // debug, start time of play

  //TrackView tv;

  /**
   * @param aWidth width of multitrack
   * @param aHeight height of multitrack
   */
  public MultiTrackView(int w,int h) {
    this.w = w;           // overall width 
    this.h = h;
    this.setPreferredSize(new Dimension(this.w, this.h));
    this.setBackground(tlDarkGray);


    addMouseListener(this);
    addMouseMotionListener(this);

    trackWidth = numSlices * sliceWidth;
    previewX =  sx + trackWidth + 5;

    trackfont = textSmallfont;  //silkfont;  // global in main class
    previewAlpha = loadImage("radial-gradient.png");//"alpha_channel.png");
    previewAlpha = previewAlpha.get(0,2,previewAlpha.width,previewAlpha.height-1);
    checkboxImg = loadImage("checkbox.gif");

    bufferTrack = new Track(numSlices, cEmpty);
    bufferTrack.active = false; // say not full of copy

    // initialize the tracks
    tracks = new Track[numTracks];
    previewColors = new Color[numTracks];
    for( int j=0; j<numTracks; j++ ) {
      tracks[j] = new Track( numSlices, cEmpty );
      tracks[j].blinkmaddr = blinkmStartAddr +j;  // set default addrs
      previewColors[j] = cEmpty;
      tracks[j].label = "Channel "+(j+1)+" Label";
    }

    changeTrack(0);

    // give people a nudge on what to do
    tracks[ currTrack ].active = true;
    tracks[ currTrack ].selects[0] =  true;

    setToolTipText(""); // register for tooltips, so getToolTipText(e) works

    reset();
  }

  /*
  public void addTrackView( TrackView tview ) {
    tv = tview;
  }
  */

  /**
   * @Override
   */
  public void paintComponent(Graphics gOG) {
    Graphics2D g = (Graphics2D) gOG;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                       RenderingHints.VALUE_ANTIALIAS_ON);
    super.paintComponent(g); 

    g.setColor( cBgDarkGray );
    g.fillRect( 0,0, getWidth(), getHeight() );

    g.setFont(trackfont);

    drawTracks(g);
    
    drawTrackButtons(g);

    drawPreview(g);

    //drawTrackMarker(g);

    drawPlayHead(g, playHeadCurr);   // it goes on top, so it gets painted last
   
  }


  public void drawTracks(Graphics2D g) {
    drawTracks(g, sx, 0, trackWidth, trackHeight);
  }

  public void drawTracks(Graphics2D g, int x, int y, int w, int h) { 
    int ty = 1 + scrubHeight;
    for( int i=0; i<numTracks; i++ ) {
      drawTrack( g, i,  x, ty+i*trackHeight, w, h  );
    }
  }

  public void drawTrack(Graphics2D g, int tracknum, int x,int y, int w, int h ) {
    g.setColor( cBgDarkGray );
    if( tracknum == currTrack ) {
      g.setColor( Color.black );
    }
    g.fillRect( x, y, w, h);
    Track track = tracks[tracknum];

    // draw slices in track
    for( int i=0; i<numSlices; i++) {
      Color c = track.slices[i];
      g.setColor( c );
      g.fillRect( x+2, y+2, sliceWidth-4, h-4 );

      boolean sel = track.selects[i];
      if( track.selects[i] ) { // if selected 
        g.setStroke( new BasicStroke(2f) );
        g.setColor(cHighLight);
        g.drawRect(x, y, sliceWidth-1, h-1 );
      }

      x += sliceWidth; // go to next slice
    }
  }

  /**
   * Hilight the currently selected track
   * hilite currTrack with marker
   */
  public void drawTrackMarker(Graphics2D g) { 
    int tx = 0; // was sx
    int ty = scrubHeight + (currTrack*trackHeight) ;
    g.setStroke( new BasicStroke(1.0f));
    g.setColor( cBgLightGray );
    //g.drawRect( tx,ty, trackWidth, trackHeight+1 );
    g.drawRect( tx,ty, w-1, trackHeight+1 );
  }

  /**
   * Draw enable & addr buttons on left side of timeline
   * @param g Graphics to draw on
   */
  public void drawTrackButtons(Graphics2D g ) {
    g.setStroke( new BasicStroke(1.0f) );
    int ty = 2 + scrubHeight ;
    int th = trackHeight - 3;
    Point mp = mousePt;
    //l.debug("drawTrackButtons: "+mp);
    for( int tnum=0; tnum<numTracks; tnum++ ) {
      Color outlinecolor = cBriOrange;

      //boolean intrack = (mp.y > tnum*trackHeight + scrubHeight) && 
      //  (mp.y < (tnum+1)*trackHeight + scrubHeight) ;
      //if( intrack ) 
      //outlinecolor = cHighLight;

      g.setColor( outlinecolor );
      g.drawRect(  8,ty+tnum*trackHeight, 15,th );  // enable button outline 
      g.drawRect( 30,ty+tnum*trackHeight, 20,th );  // addr button outline 
      
      if( tracks[tnum].active == true ) {
        g.setColor( cMuteOrange2 );
        g.fillRect(  9, ty+1+tnum*trackHeight, 14,th-1 ); // enable butt insides
        g.drawImage( checkboxImg.getImage(), 10,ty+3+tnum*trackHeight  ,null);

        int blinkmAddr = tracks[tnum].blinkmaddr; // this track's i2c address
        if( blinkmAddr != -1 ) { // if it's been set to something meaningful
          g.setStroke( new BasicStroke(1.0f) );
          g.setColor( cMuteOrange2 );
          g.fillRect( 31, ty+1+tnum*trackHeight, 19,th-1 ); // addr butt insides
          g.setColor( cDarkGray );
          int offs = 31;
          offs = ( blinkmAddr < 100 ) ? offs += 3 : offs;
          offs = ( blinkmAddr < 10 )  ? offs += 4 : offs;
          g.drawString( ""+blinkmAddr, offs, ty+13+tnum*trackHeight);//addr text
        }
      }
    }
  }

  /**
   *
   */
  public void drawPlayHead(Graphics2D g, float playHeadCurr) {
    // paint scrub area
    g.setColor(cFgLightGray);
    g.fillRect(0, 0, getWidth(), scrubHeight-spacerWidth);

    g.setStroke( new BasicStroke(0.5f) );
    // create vertical playbar
    g.setColor( playHeadC );                      //FIXME:why 10?
    g.fillRect((int)playHeadCurr, 0, spacerWidth, getHeight()-10);

    Polygon p = new Polygon();
    p.addPoint((int)playHeadCurr - 5, 0);
    p.addPoint((int)playHeadCurr + 5, 0);
    p.addPoint((int)playHeadCurr + 5, 5);
    p.addPoint((int)playHeadCurr + 1, 10);
    p.addPoint((int)playHeadCurr - 1, 10);
    p.addPoint((int)playHeadCurr - 5, 5);
    p.addPoint((int)playHeadCurr - 5, 0);    
    g.fillPolygon(p);
  }
  
  /** 
   * FIXME: color sliding doesn't work
   */
  public void drawPreview(Graphics2D g ) {
    int csn = getCurrSliceNum();
    for( int i=0; i<numTracks; i++) { 
      Color ct = tracks[i].slices[csn];
      Color c = previewColors[i];
      int rt = ct.getRed();
      int gt = ct.getGreen();
      int bt = ct.getBlue();
      int rn = c.getRed();     // 'rn' for 'red now'
      int gn = c.getGreen();
      int bn = c.getBlue();
      rn = color_slide( rn,rt, previewFadespeed);
      gn = color_slide( gn,gt, previewFadespeed);
      bn = color_slide( bn,bt, previewFadespeed);
      previewColors[i] = new Color( rn,gn,bn );
      
      int ty =  spacerWidth + scrubHeight + (i*trackHeight);
      g.setColor( previewColors[i] );
      g.fillRect( previewX , ty, previewWidth-1 , trackHeight-spacerWidth);
      g.drawImage(previewAlpha.getImage(), previewX,ty,null);
    }
  }
  
  /**
   * emulate blinkm firmware color fading
   */
  public int color_slide(int curr, int dest, int step) {
    int diff = curr - dest;
    if(diff < 0)  diff = -diff;
    
    if( diff <= step ) return dest;
    if( curr == dest ) return dest;
    else if( curr < dest ) return curr + step;
    else                   return curr - step;
  }

  // --------------------------------------------------------------------------


  /**
   *  Called once every "tick" of the application clock, usualy frameRate
   *
   */
  public void tick(float millisSinceLastTick) { 
    if( playing ) {

      // not quite sure why need to add one to durationCurrent here
      int durtmp = (durationCurrent>5) ? durationCurrent+1 : durationCurrent;
      float step = trackWidth / (durtmp*1000.0f/millisSinceLastTick);
      
      int send_addrs[] = new int[numTracks];
      Color send_colors[] = new Color[numTracks];
      int send_count=0;

      previewFadespeed = getFadeSpeed(durationCurrent);
      int newSlice = getCurrSliceNum();
      if( newSlice != currSlice ) {
        currSlice = newSlice;
        for( int i=0; i<numTracks; i++ ) {
          Color c = tracks[i].slices[currSlice];
          if( tracks[i].active ) {
            send_addrs[send_count] = tracks[i].blinkmaddr;
            if( c!=null && c != cEmpty ) { 
              send_colors[send_count] = c;
              //sendBlinkMColor( tracks[i].blinkmaddr, c );
            } else if( c == cEmpty ) {
              //sendBlinkMColor( tracks[i].blinkmaddr, cBlack );
              send_colors[send_count] = cBlack;
            }
            send_count++;
          }
        }
      }
      if( send_count > 0 ) {
        sendBlinkMColors( send_addrs, send_colors, send_count );
      }

      playHeadCurr += step;

      // FIXME: +2
      if( playHeadCurr >= sx + trackWidth +1) {   // check for end of timeline
        reset();         // rest to beginning (and stop)
        if( looping ) {  // if we loop
          play();        // start again
        } 
        else {           // or no loop, so stop after one play
          buttonPanel.setToPlay();  // set play/stop button back to play
        }
      } //if loopend
      repaint();
    } // if playing
    else {
      previewFadespeed = 1000;
    }
    //if( tv!=null) tv.tick(millisSinceLastTick);
  }

  /**
   *
   */
  public void play() {
    l.debug("starting to play for dur: " + durationCurrent);
    playing = true;
    startTime = System.currentTimeMillis();
  }

  /**
   *
   */
  public void stop() {
    l.debug("stop"); 
    playing = false;
    l.debug("elapsedTime:"+(System.currentTimeMillis() - startTime));
  }

  /**
   *
   */
  public void reset() {
    stop();
    playHeadCurr = sx;
    repaint();
  }

  /**
   * Set all timeslices to be inactive
   * FIXME: hack
   */
  public void deselectAllTracks() {
    // reset timeslice selections
    for( int i=0; i<numTracks; i++) { 
      deselectTrack( i );
    }
  }
  public void selectAllinTrack() {
      selectAll( currTrack );
  }
  /**
   *
   */
  public void selectAll( int trackindex ) {
    for( int i=0; i<numSlices; i++) {
      tracks[ trackindex ].selects[i] = true;
    }
  }
  /**
   * Sets all timeslices for a particular track to be not selected
   */
  public void deselectTrack( int trackindex ) {
    for( int i=0; i<numSlices; i++) {
      tracks[ trackindex ].selects[i] = false;
    }
  }

  public void disableAllTracks() {
    for( int i=0; i< tracks.length; i++) {
      tracks[i].active = false;
    }
    deselectAllTracks();
  }

  public void toggleTrackEnable(int track) {
    tracks[track].active = !tracks[track].active;
  }

  public void changeTrack(int newtracknum) {
    l.debug("changeTrack "+newtracknum);
    if( newtracknum < 0 ) newtracknum = 0;
    if( newtracknum == numTracks ) newtracknum = numTracks - 1;
    if( newtracknum != currTrack ) {
      copySelects(newtracknum, currTrack);
      deselectTrack(currTrack);
      currTrack = newtracknum;
      updateInfo();
      repaint();
    }
  }

  public void nextTrack() {
    changeTrack( currTrack + 1 );
  }

  public void prevTrack() {
    changeTrack( currTrack - 1 );
  }

  /** 
   * select all the slices in a given column
   * @param slicenum time slice index
   * @param state select or deselect
   */
  public void selectSlice( int slicenum, boolean state ) { 
    for( int i=0; i< numTracks; i++ ) 
      tracks[i].selects[slicenum] = state;
    repaint();
  }
  
  public void selectSlice( int tracknum, int slicenum, boolean state ) {
    tracks[tracknum].selects[slicenum] = state;
    repaint();
  }

  public void nextSlice(int modifiers) {
    int slicenum=-1;
    Track t = getCurrTrack();
    for( int i=0; i<numSlices; i++) {
      if( t.selects[i] == true ) { 
        if( modifiers==0 ) t.selects[i] = false;
        slicenum = i;
      }
    }
    if( slicenum>=0 ) {
      if( modifiers == 0 ) selectSlice(currTrack, slicenum,false);
      int nextslice = (slicenum==numSlices-1)?numSlices-1:slicenum+1;
      selectSlice(currTrack, nextslice,true);
    }
  }

  // 
  // FIXME: This is a hack, wrt modifiers and in general
  //
  public void prevSlice(int modifiers) {
    int slicenum=-1;
    Track t = getCurrTrack();
    for( int i=0; i<numSlices; i++) {
      int j = numSlices-i-1;
      if( t.selects[j] == true ) { 
        if( modifiers == 0 ) t.selects[j] = false;
        slicenum = j;
      }
    }
    if( slicenum>0 ) {
      if( modifiers == 0 ) selectSlice(currTrack, slicenum,false);
      int nextslice = (slicenum==0) ? 0 : slicenum-1;
      selectSlice(currTrack, nextslice,true);
    }              
  }

  public void toggleSlice( int slicenum ) { 
    for( int i=0; i< numTracks; i++ ) 
      tracks[i].selects[slicenum] = ! tracks[i].selects[slicenum];
    repaint();
  }

  /**
   * used by the ColorChooserPanel
   */
  public void setSelectedColor( Color c ) {
    boolean sentColor = false;
    l.debug("setSelectedColor: "+c);
    for( int i=0; i<numTracks; i++) {
      for( int j=0; j<numSlices; j++) { 
        if( tracks[i].selects[j] ) {
          tracks[i].slices[j] = c;
          if( !sentColor ) {
            sendBlinkMColor( tracks[i].blinkmaddr, c);
            sentColor=true;  // FIXME: hmmm
          }
        }
      }
    }
    repaint();
  }
 
  /**
   * For a given slice on the Tracks, return an array of all the colors
   */
  public Color[] getColorsAtSlice(int slicenum) {
    Color[] colors = new Color[numTracks];
    for( int j=0; j<numTracks; j++)  // gather up all the colors 
      colors[j] = tracks[j].slices[slicenum];
    return colors;
  }
 
  /**
   * Get the time slice index of the current playing slice
   */
  public int getCurrSliceNum() {
    int cs=0;
    for(int i=0; i<numSlices; i++) {
      if( isSliceHit( (int)playHeadCurr, i ) ) {
        cs = i;
      }
    }
    return cs;
  }
    
  /**
   *
   */
  public Track getCurrTrack() {
    return tracks[currTrack];
  }
 
  /**
   * Copy any selection from old track to new track
   */
  public void copySelects( int newtrackindex, int oldtrackindex ) {
    for( int i=0; i<numSlices; i++) 
      tracks[newtrackindex].selects[i] = tracks[oldtrackindex].selects[i];
  }
  
  /** 
   * Copy current selects to buffer
   */
  public void copy() {
    //bufferTrack.copy( tracks[currTrack] );
    bufferTrack.copy( tracks[currTrack] );
  }
  public void paste() {
    tracks[currTrack].copySlices( bufferTrack );
  }
  public void cut() { 
    copy();
    delete();
  }
  public void delete() {       
    //tracks[currTrack].erase();    // this deletes whole track
    Track t = getCurrTrack();
    for( int i=0; i<numSlices; i++ ) {
      if( t.selects[i] )
        t.slices[i] = cEmpty;
    }
  }

  // --------------------------------------------------------------------------
  
  /**
   * Returns true if mouse is within a time slice.
   * @param mx mouse x-coord
   * @param slicenum index of time slice
   */
  public boolean isSliceHit( int mx, int slicenum ) {
    return isSliceHit( mx, sx + (slicenum*sliceWidth), sliceWidth );
  }
  /**
   * Returns true if mouse is within a time slice.
   * @param mx1 mouse x-coord start pos
   * @param mx2 mouse x-coord end pos
   * @param slicenum index of time slice
   */
  public boolean isSliceHitRanged( int mx1, int mx2, int slicenum ) {
    return isSliceHitRanged( mx1, mx2, sx + (slicenum*sliceWidth), sliceWidth );
  }
  // generalized version of above
  public boolean isSliceHit(int mx, int slicex, int slicew ) {
    return (mx < (slicex + slicew ) && mx >= slicex); 
  }
  // generalized version of above
  public boolean isSliceHitRanged( int mx1,int mx2, int slicex, int slicew ) {
    if( mx2 > mx1 ) 
      return (mx1 < (slicex + slicew ) && mx2 >= slicex);
    else 
      return (mx2 < (slicex + slicew ) && mx1 >= slicex);
  }

  /**
   * give color vals on tooltip
   */
  public String getToolTipText(MouseEvent e) {
    Point mp = e.getPoint();
    for( int j=0; j<numTracks; j++) {
      boolean intrack = 
        (mp.y > j*trackHeight + scrubHeight) && 
        (mp.y < (j+1)*trackHeight + scrubHeight) ;
      if( intrack ) {
        for( int i=0; i<numSlices; i++) {
          if( isSliceHit( mp.x, i) ) {
            Color c = tracks[j].slices[i];
            if( c == cEmpty ) return "";
            return ""+c.getRed()+","+c.getGreen()+","+c.getBlue();
          }
        }
      }
    }
    return "";
  }


  public void mouseClicked(MouseEvent e) {
    //l.debug("MultiTrack.mouseClicked");
  }

  public void mouseEntered(MouseEvent e) {
    //l.debug("entered");
  }

  public void mouseExited(MouseEvent e) {
    //l.debug("exited");
  }

  /**
   * @param mp mouse point of click
   */
  public boolean isPlayheadClicked(Point mp) {
    Polygon p = new Polygon();  // creating bounding box for playhead
    p.addPoint((int)playHeadCurr - 5, 0);
    p.addPoint((int)playHeadCurr + 5, 0);
    p.addPoint((int)playHeadCurr + 5, getHeight());
    p.addPoint((int)playHeadCurr - 5, getHeight());

    return p.contains(mp);  // check if mouseclick on playhead
  }
    
  
  //
  public void mousePressed(MouseEvent e) {
    //l.debug("MultiTrackView.mousePressed: "+e);
    Point mp = e.getPoint();
    mouseClickedPt = mp;
    requestFocus();

    // playhead hits handled fully in mouseDragged
    // record location of hit in mouseClickedPt and go on
    playheadClicked = isPlayheadClicked(mp);
    if( playheadClicked ) {
      repaint();
      return;
    }
    
    // check for enable or address button hits
    for( int j=0; j<numTracks; j++) {

      boolean intrack = 
        (mp.y > j*trackHeight + scrubHeight) && 
        (mp.y < (j+1)*trackHeight + scrubHeight) ;

      if( intrack && (mp.x >= 9+0 && mp.x <=  9+20 ) ) // enable button
        toggleTrackEnable(j);
      else if( intrack && (mp.x >= 26 && mp.x <= 26+20 ) ) // addr button
        doTrackDialog(j);
      else if( intrack ) {                         // just track selection
        //copySelects(j, currTrack);
        if( currTrack != j ) {  // only deselect & change track if different
          deselectTrack( currTrack );
          changeTrack( j );
        }

        // make a gradient, from first selected color to ctrl-clicked color
        // FIXME this is somewhat unreadable
        if( (e.getModifiers() & InputEvent.CTRL_MASK) !=0) {
          int sliceClicked = sliceClicked(mouseClickedPt.x);
          int firstSlice = -1;
          for( int i=0; i<numSlices; i++ ) { 
            if( tracks[currTrack].selects[i] ) {
              firstSlice = i;
              break;
            }
          }
          if( firstSlice != -1 && sliceClicked != -1 ) {
            makeGradient( currTrack, firstSlice, sliceClicked );
          }
          return;
        }

        // change selection
        for( int i=0; i<numSlices; i++) {
          if( isSliceHit( mouseClickedPt.x, i) ) 
            selectSlice(currTrack, i,true);
          else if((e.getModifiers() & InputEvent.META_MASK) ==0) //meta not
            selectSlice(currTrack, i,false);
        }


      } // if(intrack)

    } // for all tracks
    
    //repaint();
  }

  /**
   * make a gradient on the current track, 
   * based on the colors of the start & end of the selection
   */
  public void makeGradient() {
    int start = -1;
    int end = -1;
    for( int i=0; i<numSlices; i++ ) {
      if( tracks[currTrack].selects[i] ) { 
        if( start == -1 ) {
          start = i;
        } else {
          end = i;
        }
      }
    }
    makeGradient( currTrack, start, end );
  }

  /*
   *
   */
  public void makeGradient( int tracknum, int sliceStart, int sliceEnd ) {
    int d = sliceEnd - sliceStart;
    if( d==0 ) return;
    Color sc = tracks[tracknum].slices[sliceStart];
    Color ec  = tracks[tracknum].slices[sliceEnd];
    int dr = ec.getRed()   - sc.getRed();
    int dg = ec.getGreen() - sc.getGreen();
    int db = ec.getBlue()  - sc.getBlue();
    for( int i=sliceStart; i<=sliceEnd; i++ ) {
      int r = sc.getRed()   + (dr*(i-sliceStart)/d);
      int g = sc.getGreen() + (dg*(i-sliceStart)/d);
      int b = sc.getBlue()  + (db*(i-sliceStart)/d);
      tracks[tracknum].slices[i] = new Color(r,g,b);
    }
  }

  // returns non-zero index of slice clicked
  public int sliceClicked( int x ) {
    for( int i=0; i<numSlices; i++ ) {
      if( isSliceHit( x,i) ) {
        return i;
      }
    }
    return -1;
  }
    

  public void mouseReleased(MouseEvent e) {
    Point mouseReleasedPt = e.getPoint();
    int clickCnt = e.getClickCount();

    playheadClicked = false;

    boolean intrack = 
      (mouseClickedPt.y > currTrack*trackHeight + scrubHeight) && 
      (mouseClickedPt.y < (currTrack+1)*trackHeight + scrubHeight) ;

    if( clickCnt >= 2 && intrack ) {   // double-click to set color
      l.debug("mouseReleased:doublclick!");
      //colorPreview.setColors(  getColorsAtColumn(i) );
      for( int i=0; i<numSlices; i++ ) {
        if( isSliceHit( mouseReleasedPt.x,i) ) {
          colorChooser.setColor( tracks[currTrack].slices[i] );
        }
      }
    }

  /*
    if( intrack ) {
      for( int i=0; i<numSlices; i++) {
        if( isSliceHit( mouseReleasedPt.x, i) ) {
          if((e.getModifiers() & InputEvent.META_MASK) == 0) // meta key notheld
            deselectTrack( currTrack );
          selectSlice(currTrack, i,true);
        }
      }
    }
  */

  /*
    // snap playhead to closest time slice
    for( int j=0; j<numTracks; j++ ) {
      TimeSlice[] timeSlices = timeTracks[j].timeSlices;
      for( int i=0; i<numSlices; i++) {
        TimeSlice ts = timeSlices[i];
        if( ts.selected && clickCnt >= 2 )   // double-click to set color
          //colorPreview.setColors(  getColorsAtColumn(i) );
          //colorChooser.setColor( ts.getColor());
        if( ts.isCollision((int)playHeadCurr)) {
          // update ColorPreview panel based on current pos. of slider
          //playHeadCurr = ts.x - 1;        //break;
          playHeadCurr = ts.x;        // FIXME: why was this "- 1"?
        } 
      }
    }
    */
    repaint();
  }

  public void mouseMoved(MouseEvent e) {
    mousePt = e.getPoint();
  }

  public void mouseDragged(MouseEvent e) {
    //l.debug("dragged:"+e);
    if (playheadClicked) {             // if playhead is selected move it
      playHeadCurr = e.getPoint().x;
          
      // bounds check for playhead
      if (playHeadCurr < sx)
        playHeadCurr = sx;
      else if (playHeadCurr > trackWidth)
        playHeadCurr = trackWidth;
      //if( tv!=null ) tv.playHeadCurr = playHeadCurr;
    } 
    else {
      boolean intrack = 
        (mouseClickedPt.y > currTrack*trackHeight + scrubHeight) && 
        (mouseClickedPt.y < (currTrack+1)*trackHeight + scrubHeight) ;
      if( intrack ) {
        // make multiple selection of timeslices on mousedrag
        int x = e.getPoint().x;
        for( int i=0; i<numSlices; i++) {
          if( isSliceHitRanged( x, mouseClickedPt.x, i) ) {
            selectSlice(currTrack, i,true);
          }
        }
      } // intrack
    }

    repaint();
  }


  // ------------------------------------------------------------------------


 
}
// Copyright (c) 2007-2008, ThingM Corporation

/**
 *
 */
public class SetChannelDialog extends JDialog { //implements ActionListener {

  JButton[] colorSpots;
  JTextField[] channels;
  JTextField[] labels;

  public SetChannelDialog() {
    super();

    JPanel p;
    JPanel trackpanel = new JPanel();
    trackpanel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
    trackpanel.setLayout( new BoxLayout( trackpanel, BoxLayout.Y_AXIS) );

    colorSpots = new JButton[numTracks];
    channels = new JTextField[numTracks];
    labels = new JTextField[numTracks];
    
    for( int i=0; i< numTracks; i++) { 
      colorSpots[i] = new JButton();
      channels[i]   = new JTextField(3);
      labels[i]     = new JTextField(20);

      channels[i].setHorizontalAlignment(JTextField.RIGHT);

      p = new JPanel();
      p.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
      p.add( colorSpots[i] );
      p.add( channels[i] );
      p.add( labels[i] );
      trackpanel.add( p );

      colorSpots[i].setBackground( setChannelColors[i] );
      colorSpots[i].setPreferredSize( new Dimension(20,20) );
      channels[i].setText( String.valueOf(multitrack.tracks[i].blinkmaddr) );
      labels[i].setText( String.valueOf(multitrack.tracks[i].label) );
    }

    JButton okbut = new JButton("OK");
    JButton cancelbut = new JButton("CANCEL");

    cancelbut.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          setVisible(false);  // do nothing but go away
          updateInfo();
        }
      });
    okbut.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          for( int i=0; i< numTracks; i++ ) {
            try {
              int a = Integer.parseInt( channels[i].getText() );
              if( a >=0 && a < 127 ) { // i2c limits
                multitrack.tracks[i].blinkmaddr = a;
              } else {
                println("bad value");
              }
            } catch(Exception e) {}
            multitrack.tracks[i].label = labels[i].getText();
          }
          setVisible(false);
          updateInfo();
        }
      });

    JPanel butpanel = new JPanel();
    butpanel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
    butpanel.add( okbut );
    butpanel.add( cancelbut );

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(cBgDarkGray); //sigh, gotta do this on every panel
    panel.setBorder( BorderFactory.createEmptyBorder(20,20,20,20) );

    JLabel header=new JLabel("Attached BlinkMs lit according to channel color");
    header.setForeground( cBgLightGray );
    panel.add( header, BorderLayout.NORTH );
    panel.add( trackpanel, BorderLayout.CENTER );
    panel.add( butpanel, BorderLayout.SOUTH );
 
    getContentPane().add(panel);

    pack();
    setResizable(false);
    setLocationRelativeTo(null); // center it on the BlinkMSequencer
    super.setVisible(false);

    setTitle("Set Channel");

  }

  /**
   *
   */
  public void setVisible(boolean v ) {
    super.setVisible(v);
    int addrs[] = new int[numTracks];
    Color black[] = new Color[numTracks];
    for( int i=0; i< numTracks; i++) { // ugh, wtf 
      addrs[i] = multitrack.tracks[i].blinkmaddr;
      black[i] = Color.BLACK;  // what we have here is a failure of the API :)
    }
    if( v == true ) {
      l.debug("sending blinkm colors!");
      sendBlinkMColors( addrs, setChannelColors, numTracks) ;
    } else { 
      sendBlinkMColors( addrs, black, numTracks );
      l.debug("sending blinkm all off!");
    }
  }

}


    /*
    // a dumb attempt at making table headers
    p = new JPanel();
    p.setBackground(cBgDarkGray);
    JButton fakebut = new JButton();
    fakebut.setBackground(cBgDarkGray);
    fakebut.setPreferredSize( new Dimension(20,20) );
    JTextField faketf1 = new JTextField(3);
    faketf1.setText("chan");
    faketf1.setBackground(cBgDarkGray);
    faketf1.setEditable(false);
    JTextField faketf2 = new JTextField(20);
    faketf2.setText("label");
    faketf2.setBackground(cBgDarkGray);
    faketf2.setEditable(false);
    
    p.add( fakebut );
    p.add( faketf1 );
    p.add( faketf2 );
    trackpanel.add(p);
    */

// Copyright (c) 2007-2008, ThingM Corporation

/**
 *
 */
public class Track {

  String label;
  int numSlices;

  Color[] slices;
  boolean[] selects;

  boolean isLoop = true;           // loop or no loop
  boolean active = false;

  int blinkmaddr = -1;  // default address, means "not configured"
  
  /**
   * @param numSlices number of slices in a track
   */
  public Track(int numSlices, Color cEmpty ) {
    this.numSlices = numSlices;
    this.active = false;
    this.isLoop = true;

    slices = new Color[numSlices];
    selects = new boolean[numSlices];
    
    for( int i=0; i<numSlices; i++ ) {
      slices[i] = cEmpty; // default color
      selects[i] = false;
    }

  }

  /**
   * 
   */
  public void copy(Track track) {
    this.label      = track.label;
    this.numSlices  = track.numSlices;
    this.isLoop     = track.isLoop;
    this.active     = track.active;
    this.blinkmaddr = track.blinkmaddr;

    for( int i=0; i<numSlices; i++ ) {
      slices[i]  = track.slices[i];
      selects[i] = track.selects[i];
    }
  }

  /**
   * 
   */
  public void copySlices(Track track) {
    int start = 0;
    int end = numSlices;
    int dest_start = 0;
    int dest_end = numSlices;
    for( int i=0; i<numSlices; i++ ) { 
      if( track.selects[i] ) 
        end = i;
      if( track.selects[numSlices-i-1] )
        start = numSlices-i-1;
      if( this.selects[numSlices-i-1] )
        dest_start = numSlices-i-1;
    }
    int range = end-start;
    dest_end = dest_start + range;
    if( dest_end > numSlices ) dest_end = numSlices-1;

    //println("copy "+start+"-"+end+" to "+dest_start+"-"+dest_end);
        
    for( int i=dest_start,j=start; i<dest_end+1; i++,j++ ) {
      slices[i]  = track.slices[j];
      selects[i] = track.selects[j];
    }
  }

  /**
   *
   */
  public void erase() {
    this.active = false;
    for( int i=0; i<numSlices; i++) {
      this.slices[i] = cEmpty;
      this.selects[i] = false;
    }
  }

} 


// Copyright (c) 2007-2008, ThingM Corporation

/**
 *
 */
public class Util {
  /** 
   * Returns an ImageIcon, or null if the path was invalid. 
   */
  public ImageIcon createImageIcon(String path, String description) {
    java.net.URL imgURL = getClass().getResource(path);
    if (imgURL != null) {
      return new ImageIcon(imgURL, description);
    } 
    else {
      System.err.println("Couldn't find file: " + path);
      return null;
    }
  }

  /**
   *
   */
  public void centerComp(Component c) {
    Dimension scrnSize = Toolkit.getDefaultToolkit().getScreenSize();
    c.setBounds(scrnSize.width/2 - c.getWidth()/2, scrnSize.height/2 - c.getHeight()/2, c.getWidth(), c.getHeight());
  }

  /**
   *
   */
  public JButton makeButton(String onImg, String rollImg, String txt, Color bgColor) {
    ImageIcon btnImg = createImageIcon(onImg, txt);
    JButton b = new JButton(btnImg);
    //b.setContentAreaFilled( false );
    b.setOpaque(true);
    b.setBorderPainted( false );  // set to true for debugging button sizes
    b.setBackground(bgColor);
    b.setMargin( new Insets(0,0,0,0) );

    if (rollImg != null && !rollImg.equals("")) {
      b.setRolloverEnabled(true);
      ImageIcon img = createImageIcon(rollImg, txt);
      b.setRolloverIcon(img); 
    }

    return b;
  }

}

  static public void main(String args[]) {
    PApplet.main(new String[] { "--bgcolor=#FFFFFF", "BlinkMSequencer2" });
  }
}
