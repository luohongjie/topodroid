/* @file GMActivity.java
 *
 * @author marco corvi
 * @date may 2012
 *
 * @brief TopoDroid calibration data activity
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

// import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

// import java.lang.Long;
// import java.lang.reflect.Method;
// import java.lang.reflect.InvocationTargetException;

import android.app.Application;
import android.app.Activity;
// import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.AsyncTask;
// import android.os.Handler;
// import android.os.Message;
// import android.os.Parcelable;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.DialogInterface;

import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Button;
import android.widget.Toast;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import android.view.Menu;
import android.view.MenuItem;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import android.util.FloatMath;
import android.util.Log;

public class GMActivity extends Activity
                        implements OnItemClickListener
                        , ILister
                        , IEnableButtons
                        , OnClickListener
{
  private TopoDroidApp mApp;

  private String mSaveData;                // saved GM text representation
  private TextView mSaveTextView;          // view of the saved GM
  private CalibCBlock mSaveCBlock = null;  // data of the saved GM
  private long mCIDid = -1;     // id of the GM
  private int mBlkStatus = 0;   // min display Group (can be either 1 [only active] or 0 [all])
  private int mAlgo;            // calibration algorithm

  private ListView mList;                  // display list

  private MenuItem mMIoptions;
  private MenuItem mMIdisplay;
  private MenuItem mMIhelp;

  private CalibCBlockAdapter mDataAdapter;  // adapter for the list of GM's

  private String mCalibName;
  // private ConnHandler mHandler;

  static int izons[] = { 
                        R.drawable.iz_download,
                        R.drawable.iz_toggle,
                        R.drawable.iz_numbers_no,
                        R.drawable.iz_cover,
                        R.drawable.iz_compute,
                        R.drawable.iz_read,
                        R.drawable.iz_write
                     };

  static int izonsno[] = { 
                        0,
                        R.drawable.iz_toggle_no,
                        0,
                        0,
                        0,
                        R.drawable.iz_read_no,
                        R.drawable.iz_write_no
                     };

  BitmapDrawable mBMtoggle;
  BitmapDrawable mBMtoggle_no;
  BitmapDrawable mBMread;
  BitmapDrawable mBMread_no;
  BitmapDrawable mBMwrite;
  BitmapDrawable mBMwrite_no;

  static int menus[] = {
                        R.string.menu_display,
                        R.string.menu_validate,
                        R.string.menu_options, 
                        R.string.menu_help
                     };

  static int help_icons[] = { 
                        R.string.help_download,
                        R.string.help_toggle,
                        R.string.help_group,
                        R.string.help_cover,
                        R.string.help_compute,
                        R.string.help_read,
                        R.string.help_write
                      };
  static int help_menus[] = { 
                        R.string.help_display_calib,
                        R.string.help_validate,
                        R.string.help_prefs,
                        R.string.help_help
                      };
  // -------------------------------------------------------------------
  // forward survey name to DeviceHelper

  // -------------------------------------------------------------

  /** called by CalibComputer Task
   * @return nr of iterations (neg. error)
   * @note run on an AsyncTask
   */
  int computeCalib()
  {
    long cid = mApp.mCID;
    if ( cid < 0 ) return -2;
    List<CalibCBlock> list = mApp.mDData.selectAllGMs( cid, 0 ); 
    if ( list.size() < 16 ) {
      return -1;
    }
    Calibration calibration = mApp.mCalibration;
    // FIXME set the calibration algorithm (whether non-linear or linear)
    calibration.setAlgorith( mAlgo == 2 ); // CALIB_AUTO_NON_LINEAR

    calibration.Reset( list.size() );
    for ( CalibCBlock item : list ) {
      calibration.AddValues( item );
    }
    int iter = calibration.Calibrate();
    if ( iter > 0 ) {
      float[] errors = calibration.Errors();
      for ( int k = 0; k < list.size(); ++k ) {
        CalibCBlock cb = list.get( k );
        mApp.mDData.updateGMError( cb.mId, cid, errors[k] );
        // cb.setError( errors[k] );
      }

      byte[] coeff = calibration.GetCoeff();
      mApp.mDData.updateCalibCoeff( cid, Calibration.coeffToString( coeff ) );
      mApp.mDData.updateCalibError( cid, 
             calibration.Delta(),
             calibration.Delta2(),
             calibration.MaxError(),
             iter );

      // DEBUG:
      // Calibration.logCoeff( coeff );
      // coeff = Calibration.stringToCoeff( mApp.mDData.selectCalibCoeff( cid ) );
      // Calibration.logCoeff( coeff );
    }
    // Log.v( TopoDroidApp.TAG, "iteration " + iter );
    return iter;
  }

  /** validate this calibration against another calibration
   */
  void validateCalibration( String name )
  {
    String device = mApp.distoAddress();
    if ( device == null ) return;
    Long cid = mApp.mDData.getCalibCID( name, device );
    if ( cid < 0 ) {
      return;
    }
    List<CalibCBlock> list  = mApp.mDData.selectAllGMs( mApp.mCID, 0 );
    List<CalibCBlock> list1 = mApp.mDData.selectAllGMs( cid, 0 );
    // list.addAll( list1 );
    int size  = list.size();
    int size1 = list1.size();
    if ( size < 16 || size1 < 16 ) {
      Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
      return;
    }

    String coeffStr = mApp.mDData.selectCalibCoeff( cid );
    int algo = mApp.mDData.selectCalibAlgo( cid );
    boolean nonLinear = false;
    if ( algo == 0 ) algo = mApp.getCalibAlgoFromDevice();
    if ( algo == 2 ) nonLinear = true;
    Calibration calib1 = new Calibration( Calibration.stringToCoeff( coeffStr ), nonLinear );
    // Log.v("DistoX", "Calib-1 algo " + algo );
    // calib1.dump();

    coeffStr = mApp.mDData.selectCalibCoeff( mApp.mCID );
    algo = mApp.mDData.selectCalibAlgo( mApp.mCID );
    nonLinear = false;
    if ( algo == 0 ) algo = mApp.getCalibAlgoFromDevice();
    if ( algo == 2 ) nonLinear = true;
    Calibration calib0 = new Calibration( Calibration.stringToCoeff( coeffStr ), nonLinear );
    // Log.v("DistoX", "Calib-0 algo " + algo );
    // calib0.dump();
    float[] errors0 = new float[ list.size() ]; 
    float[] errors1 = new float[ list1.size() ]; 
    int ke1 = computeErrorStats( calib0, list1, errors1 );
    int ke0 = computeErrorStats( calib1, list,  errors0 );
    double ave0 = calib0.mSumErrors / calib0.mSumCount;
    double std0 = Math.sqrt( calib0.mSumErrorSquared / calib0.mSumCount - ave0 * ave0 + 1e-8 );
    double ave1 = calib1.mSumErrors / calib1.mSumCount;
    double std1 = Math.sqrt( calib1.mSumErrorSquared / calib1.mSumCount - ave1 * ave1 + 1e-8 );
    ave0 *= TDMath.RAD2GRAD;
    std0 *= TDMath.RAD2GRAD;
    ave1 *= TDMath.RAD2GRAD;
    std1 *= TDMath.RAD2GRAD;

    list.addAll( list1 );
    size = list.size();

    float[] errors = new float[ size ];
    double err1   = 0; // average error [radians]
    double err2   = 0;
    double errmax = 0;
    int ke = 0;
    for ( CalibCBlock b : list ) {
      Vector g = new Vector( b.gx, b.gy, b.gz );
      Vector m = new Vector( b.mx, b.my, b.mz );
      Vector v0 = calib0.computeDirection(g,m);
      Vector v1 = calib1.computeDirection(g,m);
      double err = v0.minus( v1 ).Length();
      errors[ke++] = (float) err;
      err1 += err;
      err2 += err * err;
      if ( err > errmax ) errmax = err;
    }
    err1 /= size;
    err2 = Math.sqrt( err2/size - err1 * err1 );
    err1 *= TDMath.RAD2GRAD;
    err2 *= TDMath.RAD2GRAD;
    errmax *= TDMath.RAD2GRAD;
    new CalibValidateResultDialog( this, errors0, errors1, errors,
                                   ave0, std0, ave1, std1, err1, err2, errmax, name, mApp.myCalib ).show();
  }

  /** compute the error stats of the data of this calibration using the 
   * coeffiecients of another calibration
   * @param name  the other calibration name
   * @return number of errors in the array
   */
  private int computeErrorStats( Calibration calib, List<CalibCBlock> list, float[] errors )
  {
    int ke = 0; // number of errors
    for ( int c=0; c<errors.length; ++c ) errors[c] = -1;

    calib.initErrorStats();
    long group = 0;
    int k = 0;
    int cnt = 0;
    while( k < list.size() && list.get(k).mGroup <= 0 ) ++k;
    
    for ( int j=k; j<list.size(); ++j ) {
      if ( list.get(j).mGroup > 0 ) {
        if ( list.get(j).mGroup != group ) {
          if ( cnt > 0 ) {
            Vector g[] = new Vector[cnt];
            Vector m[] = new Vector[cnt];
            float  e[] = new float[cnt];
            int i=0;
            for ( ; k<j; ++k ) {
              CalibCBlock b = list.get(k);
              if ( b.mGroup == group ) {
                g[i] = new Vector( b.gx, b.gy, b.gz );
                m[i] = new Vector( b.mx, b.my, b.mz );
                e[i] = -1;
                ++i;
              }
            }
            calib.addErrorStats( g, m, e );
            for ( int c=0; c<cnt; ++c ) errors[ ke++ ] = e[c];
          }
          group = list.get(j).mGroup;
          cnt = 1;
        } else { 
          cnt ++;
        }
      }
    } 
    if ( cnt > 0 ) {
      Vector g[] = new Vector[cnt];
      Vector m[] = new Vector[cnt];
      float  e[] = new float[cnt];
      int i=0;
      for ( ; k<list.size(); ++k ) {
        CalibCBlock b = list.get(k);
        if ( b.mGroup == group ) {
          g[i] = new Vector( b.gx, b.gy, b.gz );
          m[i] = new Vector( b.mx, b.my, b.mz );
          e[i] = -1;
          ++i;
        }
      }
      calib.addErrorStats( g, m, e );
      for ( int c=0; c<cnt; ++c ) errors[ ke++ ] = e[c];
    }
    return ke;
  }


  void handleComputeCalibResult( int job, int result )
  {
    switch ( job ) {
      case CalibComputer.CALIB_COMPUTE_CALIB:
        resetTitle( );
        // ( result == -2 ) not handled
        if ( result == -1 ) {
          Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
          return;
        } else if ( result > 0 ) {
          enableWrite( true );
          Calibration calibration = mApp.mCalibration;
          Vector bg = calibration.GetBG();
          Matrix ag = calibration.GetAG();
          Vector bm = calibration.GetBM();
          Matrix am = calibration.GetAM();
          Vector nL = calibration.GetNL();
          byte[] coeff = calibration.GetCoeff();
          float[] errors = calibration.Errors();

          (new CalibCoeffDialog( this, mApp, bg, ag, bm, am, nL, errors,
                                 calibration.Delta(), calibration.Delta2(), calibration.MaxError(), 
                                 result, coeff ) ).show();
        } else {
          // Toast.makeText( mApp.getApplicationContext(), R.string.few_data, Toast.LENGTH_SHORT ).show();
          Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
          return;
        }
        break;
      case CalibComputer.CALIB_RESET_GROUPS:
        break;
      case CalibComputer.CALIB_COMPUTE_GROUPS:
      case CalibComputer.CALIB_RESET_AND_COMPUTE_GROUPS:
        if ( result < 0 ) {
          Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
        } else {
          Toast.makeText( this, "Found " + result + " groups", Toast.LENGTH_SHORT ).show();
        }
        break;
      default:
    }
    updateDisplay( );

  }

  /** called by CalibComputer Task
   * @param start_id id of the GM-data to start with
   * @note run on an AsyncTask
   */
  void doResetGroups( long start_id )
  {
    // Log.v("DistoX", "Reset CID " + mApp.mCID + " from gid " + start_id );
    mApp.mDData.resetAllGMs( mApp.mCID, start_id ); // reset all groups where status=0, and id >= start_id
  }

  /** called by CalibComputer Task
   * @note run on an AsyncTask
   */
  int doComputeGroups( long start_id )
  {
    long cid = mApp.mCID;
    // Log.v("DistoX", "Compute CID " + cid + " from gid " + start_id );
    if ( cid < 0 ) return -2;
    float thr = TDMath.cosd( TDSetting.mGroupDistance );
    List<CalibCBlock> list = mApp.mDData.selectAllGMs( cid, 0 );
    if ( list.size() < 4 ) {
      return -1;
    }
    long group = 0;
    int cnt = 0;
    float b = 0.0f;
    float c = 0.0f;
    if ( start_id >= 0 ) {
      for ( CalibCBlock item : list ) {
        if ( item.mId == start_id ) {
          group = item.mGroup;
          cnt = 1;
          b = item.mBearing;
          c = item.mClino;
          break;
        }
      }
    } else {
      if ( TDSetting.mGroupBy != TDSetting.GROUP_BY_DISTANCE ) {
        group = 1;
      }
    }
    switch ( TDSetting.mGroupBy ) {
      case TDSetting.GROUP_BY_DISTANCE:
        for ( CalibCBlock item : list ) {
          if ( start_id >= 0 && item.mId <= start_id ) continue;
          if ( group == 0 || item.isFarFrom( b, c, thr ) ) {
            ++ group;
            b = item.mBearing;
            c = item.mClino;
          }
          item.setGroup( group );
          mApp.mDData.updateGMName( item.mId, item.mCalibId, Long.toString(group) );
          // N.B. item.calibId == cid
        }
        break;
      case TDSetting.GROUP_BY_FOUR:
        // TDLog.Log( TDLog.LOG_CALIB, "group by four");
        for ( CalibCBlock item : list ) {
          if ( start_id >= 0 && item.mId <= start_id ) continue;
          item.setGroup( group );
          mApp.mDData.updateGMName( item.mId, item.mCalibId, Long.toString(group) );
          ++ cnt;
          if ( (cnt%4) == 0 ) {
            ++group;
            // TDLog.Log( TDLog.LOG_CALIB, "cnt " + cnt + " new group " + group );
          }
        }
        break;
      case TDSetting.GROUP_BY_ONLY_16:
        for ( CalibCBlock item : list ) {
          if ( start_id >= 0 && item.mId <= start_id ) continue;
          item.setGroup( group );
          mApp.mDData.updateGMName( item.mId, item.mCalibId, Long.toString(group) );
          ++ cnt;
          if ( (cnt%4) == 0 || cnt >= 16 ) ++group;
        }
        break;
    }
    return (int)group-1;
  }

  // -----------------------------------------------------------
  // ILister interface
  @Override
  public void refreshDisplay( int nr, boolean toast )
  {
    // Log.v( TopoDroidApp.TAG, "refreshDisplay nr " + nr );
    resetTitle( );
    if ( nr >= 0 ) {
      if ( nr > 0 ) updateDisplay( );
      if ( toast ) {
        Toast.makeText( this, String.format( getString(R.string.read_calib_data), nr/2, nr ), Toast.LENGTH_SHORT ).show();
      }
    } else if ( nr < 0 ) {
      if ( toast ) {
        // Toast.makeText( this, getString(R.string.read_fail_with_code) + nr, Toast.LENGTH_SHORT ).show();
        Toast.makeText( this, mApp.DistoXConnectionError[ -nr ], Toast.LENGTH_SHORT ).show();
      }
    }
  }
    
  @Override
  public void updateBlockList( DistoXDBlock blk ) { }

  @Override
  public void updateBlockList( long blk_id ) { }

  @Override
  public void setRefAzimuth( float azimuth, long fixed_extend ) { }

  @Override
  public void setConnectionStatus( int status )
  {
    /* nothing : GM data are downloaded only on-demand */
  }

  // --------------------------------------------------------------

  public void updateDisplay( )
  {
    // Log.v( TopoDroidApp.TAG, "updateDisplay CID " + mApp.mCID );
    resetTitle( );
    mDataAdapter.clear();
    if ( mApp.mDData != null && mApp.mCID >= 0 ) {
      List<CalibCBlock> list = mApp.mDData.selectAllGMs( mApp.mCID, mBlkStatus );
      // Log.v( TopoDroidApp.TAG, "updateDisplay GMs " + list.size() );
      updateGMList( list );
      setTitle( mCalibName );
    }
  }

  private void updateGMList( List<CalibCBlock> list )
  {
    int nr_saturated_values = 0;
    if ( list.size() == 0 ) {
      Toast.makeText( this, R.string.no_gms, Toast.LENGTH_SHORT ).show();
      return;
    }
    for ( CalibCBlock item : list ) {
      if ( item.isSaturated() ) ++ nr_saturated_values;
      mDataAdapter.add( item );
    }
    // mList.setAdapter( mDataAdapter );
    if ( nr_saturated_values > 0 ) {
      Toast.makeText( this, 
        String.format( getResources().getString( R.string.calib_saturated_values ), nr_saturated_values ),
        Toast.LENGTH_LONG ).show();
    }
  }


  // ---------------------------------------------------------------
  // list items click


  @Override
  public void onItemClick(AdapterView<?> parent, View view, int pos, long id)
  {
    if ( mMenu == (ListView)parent ) {
      closeMenu();
      int p = 0;
      if ( p++ == pos ) { // DISPLAY
        mBlkStatus = 1 - mBlkStatus;       // 0 --> 1;  1 --> 0
        updateDisplay( );
      } else if ( p++ == pos ) { // VALIDATE
        // Toast.makeText( this, "UNDER CONSTRUCTION", Toast.LENGTH_SHORT ).show();
        List< String > list = mApp.mDData.selectDeviceCalibs( mApp.mDevice.mAddress );
        list.remove( mApp.myCalib );
        if ( list.size() == 0 ) {
          Toast.makeText( this, R.string.few_calibs, Toast.LENGTH_SHORT ).show();
        } else {
          (new CalibValidateListDialog( this, this, list )).show();
        }
      } else if ( p++ == pos ) { // OPTIONS
        Intent intent = new Intent( this, TopoDroidPreferences.class );
        intent.putExtra( TopoDroidPreferences.PREF_CATEGORY, TopoDroidPreferences.PREF_CATEGORY_CALIB );
        startActivity( intent );
      } else if ( p++ == pos ) { // HELP
        (new HelpDialog(this, izons, menus, help_icons, help_menus, mNrButton1, 4 ) ).show();
      }
      return;
    }
    if ( onMenu ) {
      closeMenu();
      return;
    }

    CharSequence item = ((TextView) view).getText();
    String value = item.toString();
    // TDLog.Log(  TDLog.LOG_INPUT, "GMActivity onItemClick() " + item.toString() );

    // if ( value.equals( getResources().getString( R.string.back_to_calib ) ) ) {
    //   setStatus( STATUS_CALIB );
    //   updateDisplay( );
    //   return;
    // }
    mSaveCBlock   = mDataAdapter.get( pos );
    mSaveTextView = (TextView) view;
    String msg = mSaveTextView.getText().toString();
    String[] st = msg.split( " ", 3 );
    try {    
      mCIDid = Long.parseLong(st[0]);
      // String name = st[1];
      mSaveData = st[2];
      if ( mSaveCBlock.mStatus == 0 ) {
        // startGMDialog( mCIDid, st[1] );
        (new CalibGMDialog( this, this, mSaveCBlock )).show();
      } else { // FIXME TODO ask whether to undelete
        TopoDroidAlertDialog.makeAlert( this, getResources(), R.string.calib_gm_undelete,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick( DialogInterface dialog, int btn ) {
              // TDLog.Log( TDLog.LOG_INPUT, "calib delite" );
              deleteGM( false );
            }
          }
        );
      }
    } catch ( NumberFormatException e ) {
      TDLog.Error( "error: expected a long, got: " + st[0] );
    }
  }
 
  // ---------------------------------------------------------------

  private Button[] mButton1;
  private int mNrButton1 = 7;
  HorizontalListView mListView;
  HorizontalButtonView mButtonView1;
  boolean mEnableWrite;
  ListView   mMenu;
  Button     mImage;
  ArrayAdapter< String > mMenuAdapter;
  boolean onMenu;
  
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate( savedInstanceState );
    setContentView(R.layout.gm_activity);
    mApp = (TopoDroidApp) getApplication();

    mDataAdapter  = new CalibCBlockAdapter( this, R.layout.row, new ArrayList<CalibCBlock>() );

    mList = (ListView) findViewById(R.id.list);
    mList.setAdapter( mDataAdapter );
    mList.setOnItemClickListener( this );
    mList.setDividerHeight( 2 );

    // mHandler = new ConnHandler( mApp, this );
    mListView = (HorizontalListView) findViewById(R.id.listview);
    int size = mApp.setListViewHeight( mListView );
    // icons00   = ( TDSetting.mSizeButtons == 2 )? ixons : icons;
    // icons00no = ( TDSetting.mSizeButtons == 2 )? ixonsno : iconsno;

    mButton1 = new Button[ mNrButton1 ];
    for ( int k=0; k<mNrButton1; ++k ) {
      mButton1[k] = new Button( this );
      mButton1[k].setPadding(0,0,0,0);
      mButton1[k].setOnClickListener( this );
      // mButton1[k].setBackgroundResource( icons00[k] );
      BitmapDrawable bm2 = mApp.setButtonBackground( mButton1[k], size, izons[k] );
      if ( k == 1 ) {
        mBMtoggle = bm2;
      } else if ( k == 5 ) {
        mBMread = bm2;
      } else if ( k == 6 ) {
        mBMwrite = bm2;
      }
    }
    mBMtoggle_no = mApp.setButtonBackground( null, size, izonsno[1] );
    mBMread_no = mApp.setButtonBackground( null, size, izonsno[5] );
    mBMwrite_no = mApp.setButtonBackground( null, size, izonsno[6] );

    enableWrite( false );

    mButtonView1 = new HorizontalButtonView( mButton1 );
    mListView.setAdapter( mButtonView1.mAdapter );

    mCalibName = mApp.myCalib;
    mAlgo = mApp.getCalibAlgoFromDB();
    // updateDisplay( );

    mImage = (Button) findViewById( R.id.handle );
    mImage.setOnClickListener( this );
    // mImage.setBackgroundResource( ( TDSetting.mSizeButtons == 2 )? R.drawable.ix_menu : R.drawable.ic_menu );
    mApp.setButtonBackground( mImage, size, R.drawable.iz_menu );
    mMenu = (ListView) findViewById( R.id.menu );
    setMenuAdapter();
    closeMenu();
    mMenu.setOnItemClickListener( this );
  }

  private void resetTitle()
  {
    setTitle( mCalibName );
    if ( mBlkStatus == 0 ) {
      setTitleColor( TopoDroidConst.COLOR_NORMAL );
    } else {
      setTitleColor( TopoDroidConst.COLOR_NORMAL2 );
    }
  }

  private void enableWrite( boolean enable ) 
  {
    mEnableWrite = enable;
    mButton1[6].setEnabled( enable );
    if ( enable ) {
      // mButton1[6].setBackgroundResource( icons00[6] );
      mButton1[6].setBackgroundDrawable( mBMwrite );
    } else {
      // mButton1[6].setBackgroundResource( icons00no[6] );
      mButton1[6].setBackgroundDrawable( mBMwrite_no );
    }
  }

  @Override
  public void enableButtons( boolean enable )
  {
    mButton1[1].setEnabled( enable );
    mButton1[5].setEnabled( enable );
    mButton1[6].setEnabled( enable && mEnableWrite );
    if ( enable ) {
      setTitleColor( TopoDroidConst.COLOR_NORMAL );
      // mButton1[1].setBackgroundResource( icons00[1] );
      // mButton1[5].setBackgroundResource( icons00[5] );
      mButton1[1].setBackgroundDrawable( mBMtoggle );
      mButton1[5].setBackgroundDrawable( mBMread );
    } else {
      setTitleColor( TopoDroidConst.COLOR_CONNECTED );
      // mButton1[1].setBackgroundResource( icons00no[1] );
      // mButton1[5].setBackgroundResource( icons00no[5] );
      mButton1[1].setBackgroundDrawable( mBMtoggle_no );
      mButton1[5].setBackgroundDrawable( mBMread_no );
    }
    if ( enable && mEnableWrite ) {
      // mButton1[6].setBackgroundResource( icons00[6] );
      mButton1[6].setBackgroundDrawable( mBMwrite );
    } else {
      // mButton1[6].setBackgroundResource( icons00no[6] );
      mButton1[6].setBackgroundDrawable( mBMwrite_no );
    }
  }

    public void onClick(View view)
    {
      if ( onMenu ) {
        closeMenu();
        return;
      }

      Button b = (Button)view;

      if ( b == mImage ) {
        if ( mMenu.getVisibility() == View.VISIBLE ) {
          mMenu.setVisibility( View.GONE );
          onMenu = false;
        } else {
          mMenu.setVisibility( View.VISIBLE );
          onMenu = true;
        }
        return;
      }

      if ( b == mButton1[0] ) { // download
        if ( ! mApp.checkCalibrationDeviceMatch() ) {
          Toast.makeText( this, R.string.calib_device_mismatch, Toast.LENGTH_LONG ).show();
        } else {
          enableWrite( false );
          setTitleColor( TopoDroidConst.COLOR_CONNECTED );
          if ( mAlgo == 0 ) { // CALIB_ALGO_AUTO
            mAlgo = mApp.getCalibAlgoFromDevice();
            if ( mAlgo < 0 ) { // CALIB_ALGO_AUTO
              Toast.makeText( this, R.string.device_algo_failed, Toast.LENGTH_SHORT ).show();
              mAlgo = 1; // CALIB_ALGO_LINEAR
            }
            mApp.updateCalibAlgo( mAlgo );
          }
          ListerHandler handler = new ListerHandler( this ); // FIXME LISTER
          new DataDownloadTask( mApp, handler ).execute();
          // new DataDownloadTask( mApp, this ).execute();
        }
      } else if ( b == mButton1[1] ) { // TOGGLE
        enableButtons( false );
        new CalibToggleTask( this, this, mApp ).execute();
      } else if ( b == mButton1[2] ) { // GROUP
        if ( mApp.mCID >= 0 ) {
          List< CalibCBlock > list = mApp.mDData.selectAllGMs( mApp.mCID, 0 );
          if ( list.size() >= 16 ) {
            (new GMGroupsDialog( this, this, 
              ( TDSetting.mGroupBy == TDSetting.GROUP_BY_DISTANCE )?
                getResources().getString( R.string.group_policy_distance )
              : ( TDSetting.mGroupBy == TDSetting.GROUP_BY_FOUR )?
                getResources().getString( R.string.group_policy_four )
              : /* TDSetting.GROUP_BY_ONLY_16 */
                getResources().getString( R.string.group_policy_sixteen ) 
            )).show();
            // new CalibComputer( this, -1L, CalibComputer.CALIB_COMPUTE_GROUPS ).execute();
          } else {
            resetTitle( );
            Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
          }
        } else {
          resetTitle( );
          Toast.makeText( this, R.string.no_calibration, Toast.LENGTH_SHORT ).show();
        }
      } else if ( b == mButton1[3] ) { // COVER
        Calibration calib = mApp.mCalibration;
        if ( calib != null ) {
          List< CalibCBlock > list = mApp.mDData.selectAllGMs( mApp.mCID, 0 );
          if ( list.size() >= 16 ) {
            ( new CalibCoverageDialog( this, list, calib ) ).show();
          } else {
            Toast.makeText( this, R.string.few_data, Toast.LENGTH_SHORT ).show();
          }
        } else {
          Toast.makeText( this, R.string.no_calibration, Toast.LENGTH_SHORT ).show();
        }
      } else if ( b == mButton1[4] ) { // COMPUTE
        if ( mApp.mCID >= 0 ) {
          setTitle( R.string.calib_compute_coeffs );
          setTitleColor( TopoDroidConst.COLOR_COMPUTE );
          if ( mAlgo == 0 ) { // CALIB_ALGO_AUTO
            mAlgo = ( TDSetting.mCalibAlgo != 0 ) ? TDSetting.mCalibAlgo : 1; // CALIB_AUTO_LINEAR
            mApp.updateCalibAlgo( mAlgo );
          }
          new CalibComputer( this, -1L, CalibComputer.CALIB_COMPUTE_CALIB ).execute();
        } else {
          Toast.makeText( this, R.string.no_calibration, Toast.LENGTH_SHORT ).show();
        }
      } else if ( b == mButton1[5] ) { // READ
        enableButtons( false );
        new CalibReadTask( this, this, mApp, CalibReadTask.PARENT_GM ).execute(); // 

      } else if ( b == mButton1[6] ) { // WRITE
        // if ( mEnableWrite ) {
          if ( mApp.mCalibration == null ) {
            Toast.makeText( this, R.string.no_calibration, Toast.LENGTH_SHORT).show();
          } else {
            setTitle( R.string.calib_write_coeffs );
            setTitleColor( TopoDroidConst.COLOR_CONNECTED );

            byte[] coeff = mApp.mCalibration.GetCoeff();
            if ( coeff == null ) {
              Toast.makeText( this, R.string.no_calibration, Toast.LENGTH_SHORT).show();
            } else {
              mApp.uploadCalibCoeff( this, coeff );
            }
            resetTitle( );
          }
        // }
      // } else if ( b == mButton1[7] ) { // disto
      //   Intent deviceIntent = new Intent( Intent.ACTION_EDIT ).setClass( this, DeviceActivity.class );
      //   startActivity( deviceIntent );
      }
    }

  void computeGroups( long start_id )
  {
    setTitle( R.string.calib_compute_groups );
    setTitleColor( TopoDroidConst.COLOR_COMPUTE );
    new CalibComputer( this, start_id, CalibComputer.CALIB_COMPUTE_GROUPS ).execute();
  }

  void resetGroups( long start_id )
  {
    new CalibComputer( this, start_id, CalibComputer.CALIB_RESET_GROUPS ).execute();
  }

  void resetAndComputeGroups( long start_id )
  {
    setTitle( R.string.calib_compute_groups );
    setTitleColor( TopoDroidConst.COLOR_COMPUTE );
    new CalibComputer( this, start_id, CalibComputer.CALIB_RESET_AND_COMPUTE_GROUPS ).execute();
  }


  // ------------------------------------------------------------------
  // LIFECYCLE
  //
  // onCreate --> onStart --> onResume
  //          --> onSaveInstanceState --> onPause --> onStop | drawing | --> onStart --> onResume
  //          --> onSaveInstanceState --> onPause [ off/on ] --> onResume
  //          --> onPause --> onStop --> onDestroy

  @Override
  public void onStart()
  {
    super.onStart();
    // setBTMenus( mApp.mBTAdapter.isEnabled() );
  }

  @Override
  public synchronized void onResume() 
  {
    super.onResume();
    // if ( mApp.mComm != null ) { mApp.mComm.resume(); }
    // Log.v( TopoDroidApp.TAG, "onResume ");
    updateDisplay( );
    // mApp.registerConnListener( mHandler );
    mApp.mGMActivityVisible = true;
  }

  @Override
  protected synchronized void onPause() 
  { 
    super.onPause();
    mApp.mGMActivityVisible = false;
    // mApp.unregisterConnListener( mHandler );
    // if ( mApp.mComm != null ) { mApp.mComm.suspend(); }
  }


  // @Override
  // public synchronized void onStop()
  // { 
  //   super.onStop();
  // }

  // @Override
  // public synchronized void onDestroy() 
  // {
  //   super.onDestroy();
  // }

  // ------------------------------------------------------------------

  // public int downloadDataBatch()
  // {
  //   ArrayList<ILister> listers = new ArrayList<ILister>();
  //   listers.add( this );
  //   return mApp.downloadDataBatch( this );
  // }

  // public void makeNewCalib( String name, String date, String comment )
  // {
  //   long id = setCalibFromName( name );
  //   if ( id > 0 ) {
  //     mApp.mDData.updateCalibDayAndComment( id, date, comment );
  //     setStatus( STATUS_GM );
  //     // updateDisplay( );
  //   }
  // }
 
  void updateGM( long value, String name )
  {
    mApp.mDData.updateGMName( mCIDid, mApp.mCID, name );
    String id = (new Long(mCIDid)).toString();
    // CalibCBlock blk = mApp.mDData.selectGM( mCIDid, mApp.mCID );
    mSaveCBlock.setGroup( value );

    // if ( mApp.mListRefresh ) {
    //   mDataAdapter.notifyDataSetChanged();
    // } else {
      mSaveTextView.setText( id + " <" + name + "> " + mSaveData );
      mSaveTextView.setTextColor( mSaveCBlock.color() );
      // mSaveTextView.invalidate();
      // updateDisplay( ); // FIXME
    // }
  }

  void deleteGM( boolean delete )
  {
    mApp.mDData.deleteGM( mApp.mCID, mCIDid, delete );
    updateDisplay( );
  }


  @Override
  public boolean onSearchRequested()
  {
    // TDLog.Error( "search requested" );
    Intent intent = new Intent( this, TopoDroidPreferences.class );
    intent.putExtra( TopoDroidPreferences.PREF_CATEGORY, TopoDroidPreferences.PREF_CATEGORY_CALIB );
    startActivity( intent );
    return true;
  }

  @Override
  public boolean onKeyDown( int code, KeyEvent event )
  {
    switch ( code ) {
      case KeyEvent.KEYCODE_BACK: // HARDWARE BACK (4)
        super.onBackPressed();
        return true;
      case KeyEvent.KEYCODE_SEARCH:
        return onSearchRequested();
      case KeyEvent.KEYCODE_MENU:   // HARDWRAE MENU (82)
        String help_page = getResources().getString( R.string.GMActivity );
        if ( help_page != null ) UserManualActivity.showHelpPage( this, help_page );
        return true;
      // case KeyEvent.KEYCODE_VOLUME_UP:   // (24)
      // case KeyEvent.KEYCODE_VOLUME_DOWN: // (25)
      default:
        // TDLog.Error( "key down: code " + code );
    }
    return false;
  }

  // ---------------------------------------------------------

  private void setMenuAdapter()
  {
    Resources res = getResources();
    mMenuAdapter = new ArrayAdapter<String>(this, R.layout.menu );
    for ( int k=0; k<4; ++k ) {
      mMenuAdapter.add( res.getString( menus[k] ) );
    }
    mMenu.setAdapter( mMenuAdapter );
    mMenu.invalidate();
  }

  private void closeMenu()
  {
    mMenu.setVisibility( View.GONE );
    onMenu = false;
  }

  // public void notifyDisconnected()
  // {
  // }

}
