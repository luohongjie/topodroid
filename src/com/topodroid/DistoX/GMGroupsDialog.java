/** @file GMGroupsDialog.java
 *
 * @author marco corvi
 * @date may 2012
 *
 * @brief TopoDroid calibration data dialog
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import android.app.Dialog;
import android.os.Bundle;
import android.content.Context;

import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.Button;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;

class GMGroupsDialog extends MyDialog
                     implements OnClickListener
{
  private GMActivity mParent;

  private CheckBox mCBreset;
  private Button mBtnOK;

  private String mPolicy;

  GMGroupsDialog( Context context, GMActivity parent, String policy )
  {
    super( context, R.string.GMGroupsDialog );
    mParent  = parent;
    mPolicy  = policy;
  }

  @Override
  public void onCreate( Bundle bundle )
  {
    super.onCreate( bundle );

    initLayout( R.layout.gm_groups_dialog, R.string.group_title );
    
    mBtnOK = (Button) findViewById( R.id.group_ok );
    mBtnOK.setOnClickListener( this );
    
    TextView policy = (TextView) findViewById( R.id.group_policy );
    policy.setText( mPolicy );

    mCBreset = (CheckBox) findViewById( R.id.group_reset );
    mCBreset.setChecked( false );
  }
    
  @Override
  public void onClick( View v ) 
  {
    Button b = (Button)v;
    if ( b == mBtnOK ) {
      if ( mCBreset.isChecked() ) {
        mParent.resetGroups( -1L );
      } else {
        mParent.computeGroups( -1L );
      }
    }
    dismiss();
  }
}
