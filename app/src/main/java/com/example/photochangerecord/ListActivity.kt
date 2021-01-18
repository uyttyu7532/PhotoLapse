package com.example.photochangerecord


import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photochangerecord.databinding.ActivityListBinding
import com.example.photochangerecord.databinding.AddFolderDialogBinding
import com.example.photochangerecord.viewmodel.CameraBackGroundViewModel
import com.example.photochangerecord.viewmodel.Folder
import com.example.photochangerecord.viewmodel.Photo
import com.takusemba.multisnaprecyclerview.MultiSnapHelper
import com.takusemba.multisnaprecyclerview.SnapGravity
import splitties.toast.toast
import java.io.File


class ListActivity : AppCompatActivity() {
    private var mContext: Context? = null
    private val folderList: ArrayList<Folder> = ArrayList()

    companion object {
        private const val TAG = "ListActivity"
    }

    private lateinit var binding: ActivityListBinding
    private lateinit var viewModel: CameraBackGroundViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this

        binding = DataBindingUtil.setContentView(this, R.layout.activity_list)

        // 뷰모델 인스턴스를 가져온다.
        viewModel = ViewModelProvider(this).get(CameraBackGroundViewModel::class.java)
        // 원래 this로 액티비티를 연결했지만 뷰모델을 여기서 연결한다!
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        val actionBar: ActionBar? = supportActionBar
        actionBar!!.hide()


        binding.newFolderFab.setOnClickListener {
            // 폴더 생성 다이얼로그
            showMakeFolderDialog()

        }

    }

    override fun onResume() {
        recyclerview(getFolderName())
        super.onResume()
    }

    private fun getFolderName(): ArrayList<String> {

        var directory = File(
            getExternalFilesDir(
                Environment.DIRECTORY_DCIM
            ).toString()
        )
        var files = directory.listFiles();

        var filesNameList: ArrayList<String> = ArrayList()

        for (f in files) {
            filesNameList.add(f.name)
        }

        return filesNameList

    }


    private fun makeNewFolder(folderName: String, CallBack: (isExisted: Boolean) -> Unit) {
        // TODO 중복확인

        val dir = File(
            getExternalFilesDir(
                Environment.DIRECTORY_DCIM
            ).toString() + "/$folderName"
        )

        Log.d(TAG, "makeNewFolder: ${dir.absolutePath}")

        if (!dir!!.exists()) {
            dir.mkdirs()
            CallBack(false)
            recyclerview(getFolderName())
        } else {
            toast("Existed Folder Name")
            CallBack(true)
        }
    }

    private fun deleteFolder(folderName: String) {
        // TODO
    }

    private fun showMakeFolderDialog() {
        val binding: AddFolderDialogBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.add_folder_dialog,
            null,
            false
        )

        val dialog = Dialog(this)

        binding.dialogAgreeBtn.setOnClickListener {

            makeNewFolder(binding.dialogEt.text.toString(), CallBack = {
                if(!it){
                    dialog.dismiss()
                }

            })

        }

        binding.dialogDisagreeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(binding.root)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun recyclerview(folderNameList: ArrayList<String>) {


        val adapter = VerticalAdapter(mContext!!, folderNameList)
        adapter.itemClick = object : VerticalAdapter.ItemClick {
            override fun onClick(view: View, position: Int, folderName: String) {
                Log.d(TAG, "onClick: $position clicked")

                // GalleryActivity에서 업데이트 될 수도 있으니까
                val intent = Intent(mContext, GalleryActivity::class.java)
                intent.putExtra("folderName", folderName)
                startActivity(intent)
            }

            override fun addBtnOnClick(view: View, position: Int, folderName: String) {
                val intent = Intent(mContext, LaunchActivity::class.java)
                intent.putExtra("folderName", folderName)
                startActivity(intent)
            }
        }

        val recyclerView = binding.recyclerViewVertical
        val manager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = manager
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        recyclerView.adapter = adapter

//        val multiSnapHelper = MultiSnapHelper(SnapGravity.START, 1, 100f)
//        multiSnapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    binding.newFolderFab.hide()
                } else {
                    binding.newFolderFab.show()
                }
                super.onScrolled(recyclerView, dx, dy)

            }
        })
    }


}