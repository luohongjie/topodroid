/* @file ILister.java
 *
 * @author marco corvi
 * @date dec 2011
 *
 * @brief TopoDroid handler for a data lister
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 * CHANGES
 */
package com.topodroid.DistoX;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

public class ListerHandler extends Handler 
{
  final String LISTER_DATA_NUMBER    = "LISTER_DATA_NUMBER";
  final String LISTER_DATA_STATUS    = "LISTER_DATA_STATUS";
  final String LISTER_DATA_BLOCK_ID  = "LISTER_DATA_BLOCK_ID";
  final String LISTER_DATA_AZIMUTH   = "LISTER_DATA_AZIMUTH";
  final String LISTER_DATA_FIXED_EXTEND  = "LISTER_DATA_FIXED_EXTEND";

  final int LISTER_REFRESH = 1;
  final int LISTER_UPDATE  = 2;
  final int LISTER_STATUS  = 3;
  final int LISTER_REF_AZIMUTH = 4;

  ILister mLister;

  ListerHandler( ILister lister )
  { 
    mLister = lister;
  }

  @Override
  public void handleMessage( Message msg )
  {
    Bundle bundle = msg.getData();
    switch ( msg.what ) {
      case LISTER_REFRESH:
        int nr = bundle.getInt( LISTER_DATA_NUMBER );
        mLister.refreshDisplay( nr, false );
        break;
      case LISTER_STATUS:
        int status = bundle.getInt( LISTER_DATA_STATUS );
        mLister.setConnectionStatus( status );
        break;
      case LISTER_UPDATE:
        long blk_id = bundle.getLong( LISTER_DATA_BLOCK_ID );
        mLister.updateBlockList( blk_id );
        break;
      case LISTER_REF_AZIMUTH:
        float azimuth =  bundle.getFloat( LISTER_DATA_AZIMUTH );
        long fixed_extend = bundle.getLong( LISTER_DATA_FIXED_EXTEND );
        mLister.setRefAzimuth( azimuth, fixed_extend );
        break;
    }
  }

}

