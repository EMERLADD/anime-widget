package com.animewidget.maid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

class CharacterSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var primary: TextView; private lateinit var secondary: TextView
    private lateinit var themeModes: RadioGroup
    private lateinit var autoMode: RadioButton; private lateinit var manualMode: RadioButton; private lateinit var classicMode: RadioButton
    private lateinit var manualPrimary: TextView; private lateinit var manualSecondary: TextView; private lateinit var manualText: TextView; private lateinit var manualBubbleText: TextView
    private lateinit var appFollowTheme: Switch
    private lateinit var advancedContainer: LinearLayout; private lateinit var advancedToggle: Button
    private lateinit var skinOn: Switch; private lateinit var skinStrength: EditText
    private lateinit var bwPrimary: Switch; private lateinit var bwSecondary: Switch
    private lateinit var bwWeight: EditText
    private lateinit var sampleAlphaMin: EditText; private lateinit var minimumColorDistance: EditText
    private lateinit var whiteLuminanceMin: EditText; private lateinit var whiteSaturationMax: EditText
    private lateinit var blackLuminanceMax: EditText
    private lateinit var skinHueMin: EditText; private lateinit var skinHueMax: EditText
    private lateinit var skinSaturationMin: EditText; private lateinit var skinSaturationMax: EditText
    private lateinit var skinValueMin: EditText
    private var config = JSONObject(); private var preferences = JSONObject(); private var currentTheme = JSONObject(); private var busy = false
    private var loadingPreferences = true; private var manualInitialized = false
    private var colorDialog: AlertDialog? = null
    private var manualPrimaryHex = "#48A4F2"; private var manualSecondaryHex = "#AEDCFA"; private var manualTextHex = "#FFFFFF"; private var manualBubbleTextHex = "#FFFFFF"

    override fun onCreate(state: Bundle?) { super.onCreate(state); title = "替换角色"; setContentView(ui()); AppThemeManager.apply(this); load() }
    override fun onResume() { super.onResume(); AppThemeManager.apply(this) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(s: String) = TextView(this).apply { text=s; setPadding(0,dp(8),0,dp(3)) }
    private fun number(default: String) = EditText(this).apply { setText(default); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setBackgroundColor(Color.rgb(240,247,253)); setPadding(dp(12),dp(8),dp(12),dp(8)) }
    private fun button(s: String, f: () -> Unit) = Button(this).apply { text=s; setOnClickListener { f() } }
    private fun ui(): View = ScrollView(this).apply { addView(LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(18),dp(22),dp(30))
        addView(TextView(context).apply { text="角色与主题"; textSize=26f; gravity=Gravity.CENTER; setTextColor(Color.rgb(24,86,150)) })
        addView(TextView(context).apply { text="导入透明 PNG；只提取两个主要颜色，其余颜色自动生成。"; gravity=Gravity.CENTER; setPadding(0,dp(5),0,dp(16)) })
        addView(button("选择角色 PNG") { choose() }); addView(button("恢复默认角色") { restore() })
        addView(label("主题模式"))
        themeModes=RadioGroup(context).apply {
            orientation=RadioGroup.VERTICAL
            autoMode=RadioButton(context).apply { text="自动取色（跟随角色图片）"; id=View.generateViewId() }.also { addView(it) }
            manualMode=RadioButton(context).apply { text="手动调色"; id=View.generateViewId() }.also { addView(it) }
            classicMode=RadioButton(context).apply { text="经典蓝白默认（勾选即使用默认）"; id=View.generateViewId() }.also { addView(it) }
            setOnCheckedChangeListener { _, checkedId ->
                if (!loadingPreferences && checkedId == manualMode.id && !manualInitialized) copyCurrentThemeToManual(true)
                updateManualEnabled()
            }
        }.also { addView(it) }
        addView(TextView(context).apply { text="手动模式下点击色块选色；拖动调色时不会刷新小组件。"; textSize=12f; setPadding(0,dp(4),0,dp(7)) })
        manualPrimary=colorChoice("主题色一",manualPrimaryHex) { pickColor("主题色一",manualPrimaryHex) { manualPrimaryHex=it; paintChoice(manualPrimary,"主题色一",it) } }.also { addView(it) }
        manualSecondary=colorChoice("主题色二",manualSecondaryHex) { pickColor("主题色二",manualSecondaryHex) { manualSecondaryHex=it; paintChoice(manualSecondary,"主题色二",it) } }.also { addView(it) }
        manualText=colorChoice("字体颜色",manualTextHex) { pickColor("字体颜色",manualTextHex) { manualTextHex=it; paintChoice(manualText,"字体颜色",it) } }.also { addView(it) }
        manualBubbleText=colorChoice("对话文字颜色",manualBubbleTextHex) { pickColor("对话文字颜色",manualBubbleTextHex) { manualBubbleTextHex=it; paintChoice(manualBubbleText,"对话文字颜色",it) } }.also { addView(it) }
        addView(button("从当前主题开始编辑") { copyCurrentThemeToManual(true) })
        appFollowTheme=Switch(context).apply { text="让应用界面跟随小组件主题" }.also { addView(it) }
        addView(button("应用主题") { applyTheme() })
        addView(label("当前主题色")); primary=colorLabel("主色").also { addView(it) }; secondary=colorLabel("次色").also { addView(it) }
        advancedToggle=button("展开高级自动取色设置 ▾") { setAdvancedVisible(advancedContainer.visibility != View.VISIBLE) }.also { addView(it) }
        advancedContainer=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; visibility=View.GONE
            addView(TextView(context).apply { text="以下设置仅影响自动取色；在其他主题模式下修改后，会在下次使用自动取色时生效。"; textSize=12f; setPadding(0,dp(8),0,dp(5)) })
            skinOn=Switch(context).apply { text="过滤肤色及接近肤色" }.also { addView(it) }
            addView(label("肤色过滤强度（0–1）")); skinStrength=number("1.0").also { addView(it) }
            bwPrimary=Switch(context).apply { text="允许纯黑/纯白作为主色" }.also { addView(it) }
            bwSecondary=Switch(context).apply { text="允许纯黑/纯白作为次色" }.also { addView(it) }
            addView(label("纯黑/纯白取色权重（0–1）")); bwWeight=number("0.25").also { addView(it) }
            addView(label("最低像素透明度（0–255 整数）")); sampleAlphaMin=number("48").also { addView(it) }
            addView(label("两个主色的最小色差（0–441）")); minimumColorDistance=number("42").also { addView(it) }
            addView(label("白色最低亮度（0–1）")); whiteLuminanceMin=number("0.92").also { addView(it) }
            addView(label("白色最高饱和度（0–1）")); whiteSaturationMax=number("0.10").also { addView(it) }
            addView(label("黑色最高亮度（0–1）")); blackLuminanceMax=number("0.08").also { addView(it) }
            addView(label("肤色最小色相（0–360）")); skinHueMin=number("5").also { addView(it) }
            addView(label("肤色最大色相（0–360）")); skinHueMax=number("48").also { addView(it) }
            addView(label("肤色最低饱和度（0–1）")); skinSaturationMin=number("0.10").also { addView(it) }
            addView(label("肤色最高饱和度（0–1）")); skinSaturationMax=number("0.78").also { addView(it) }
            addView(label("肤色最低明度（0–1）")); skinValueMin=number("0.30").also { addView(it) }
            addView(button("保存设置并重新分析当前角色") { save() })
            addView(TextView(context).apply { text="默认：肤色过滤 1.0（黑名单）；黑白不能作为主色、可作为次色，权重 0.25。"; textSize=12f; setPadding(0,dp(10),0,0) })
        }.also { addView(it) }
    }) }
    private fun setAdvancedVisible(visible:Boolean){
        advancedContainer.visibility=if(visible)View.VISIBLE else View.GONE
        advancedToggle.text=if(visible)"收起高级自动取色设置 ▴" else "展开高级自动取色设置 ▾"
    }
    private fun colorLabel(name: String)=TextView(this).apply { tag=AppThemeManager.SKIP_TAG;  text="$name：读取中…"; textSize=16f; setPadding(dp(12),dp(10),dp(12),dp(10)) }
    private fun colorChoice(name:String,hex:String,onClick:()->Unit)=TextView(this).apply { tag=AppThemeManager.SKIP_TAG; textSize=16f; setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { onClick() }; paintChoice(this,name,hex) }
    private fun choose() { if (!busy) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="image/png" }, 4102) }
    @Deprecated("Compatibility API") override fun onActivityResult(code:Int,result:Int,data:Intent?) { super.onActivityResult(code,result,data); if(code==4102&&result==RESULT_OK)data?.data?.let(::importPng) }
    private fun importPng(uri: Uri)=operate("角色已导入；主题按当前模式处理") {
        val tmp=File.createTempFile("character-import-",".png",cacheDir)
        try { contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: error("无法读取所选文件"); ensure(PythonBridge.importCharacterPng(this,tmp.absolutePath)); rerender() } finally { tmp.delete() }
    }
    private fun restore()=operate("已恢复默认角色；主题按当前模式处理") { ensure(PythonBridge.restoreDefaultCharacter(this)); rerender() }
    private fun selectedMode()=when(themeModes.checkedRadioButtonId){manualMode.id->"manual";classicMode.id->"classic";else->"auto"}
    private fun applyTheme() {
        val next=JSONObject(preferences.toString()).put("mode",selectedMode()).put("manual_initialized",manualInitialized).put("app_follow_theme",appFollowTheme.isChecked).put("manual",JSONObject().put("primary",manualPrimaryHex).put("secondary",manualSecondaryHex).put("text",manualTextHex).put("bubble_text",manualBubbleTextHex))
        operate("主题已应用") { ensure(PythonBridge.saveThemePreferencesJson(this,next.toString())); preferences=next; rerender() }
    }
    private fun save() {
        val strength=value(skinStrength,"肤色过滤强度",0.0,1.0)?:return
        val weight=value(bwWeight,"黑白权重",0.0,1.0)?:return
        val alpha=integerValue(sampleAlphaMin,"最低像素透明度",0,255)?:return
        val distance=value(minimumColorDistance,"最小色差",0.0,441.0)?:return
        val whiteLum=value(whiteLuminanceMin,"白色最低亮度",0.0,1.0)?:return
        val whiteSat=value(whiteSaturationMax,"白色最高饱和度",0.0,1.0)?:return
        val blackLum=value(blackLuminanceMax,"黑色最高亮度",0.0,1.0)?:return
        val hueMin=value(skinHueMin,"肤色最小色相",0.0,360.0)?:return
        val hueMax=value(skinHueMax,"肤色最大色相",0.0,360.0)?:return
        val satMin=value(skinSaturationMin,"肤色最低饱和度",0.0,1.0)?:return
        val satMax=value(skinSaturationMax,"肤色最高饱和度",0.0,1.0)?:return
        val valueMin=value(skinValueMin,"肤色最低明度",0.0,1.0)?:return
        if (hueMin > hueMax) { skinHueMax.error="肤色最大色相不能小于最小色相"; return }
        if (satMin > satMax) { skinSaturationMax.error="肤色最高饱和度不能小于最低饱和度"; return }
        val next=JSONObject(config.toString())
        val skin=next.optJSONObject("skin_filter")?:JSONObject().also{next.put("skin_filter",it)}
        val bw=next.optJSONObject("white_black")?:JSONObject().also{next.put("white_black",it)}
        skin.put("enabled",skinOn.isChecked).put("strength",strength).put("hue_min",hueMin).put("hue_max",hueMax).put("saturation_min",satMin).put("saturation_max",satMax).put("value_min",valueMin)
        bw.put("allow_as_primary",bwPrimary.isChecked).put("allow_as_secondary",bwSecondary.isChecked).put("weight",weight).put("white_luminance_min",whiteLum).put("white_saturation_max",whiteSat).put("black_luminance_max",blackLum)
        next.put("sample_alpha_min",alpha).put("minimum_color_distance",distance)
        operate("取色设置已保存并重新分析") { config=JSONObject(ensure(PythonBridge.saveThemeExtractionJson(this,next.toString()))); rerender() }
    }
    private fun integerValue(e:EditText,n:String,min:Int,max:Int):Int? { val v=e.text.toString().trim().toIntOrNull(); if(v==null||v !in min..max){e.error="整数超出允许范围";return null};return v }
    private fun value(e:EditText,n:String,min:Double,max:Double):Double? { val v=e.text.toString().trim().toDoubleOrNull(); if(v==null||v !in min..max){e.error="数值超出允许范围";return null};return v }
    private fun load() { scope.launch { try {
        val values=withContext(Dispatchers.IO){arrayOf(PythonBridge.getThemeExtractionJson(this@CharacterSettingsActivity),PythonBridge.getThemePreferencesJson(this@CharacterSettingsActivity),PythonBridge.getCharacterThemeJson(this@CharacterSettingsActivity))}
        config=JSONObject(values[0]); preferences=JSONObject(values[1]); currentTheme=JSONObject(values[2]); showConfig(); showPreferences(); showTheme(currentTheme)
    }catch(e:Exception){toast("读取设置失败：${e.message}")} } }
    private fun showConfig(){
        val s=config.optJSONObject("skin_filter")?:JSONObject(); val b=config.optJSONObject("white_black")?:JSONObject()
        skinOn.isChecked=s.optBoolean("enabled",true); skinStrength.setText(s.optDouble("strength",1.0).toString())
        bwPrimary.isChecked=b.optBoolean("allow_as_primary",false); bwSecondary.isChecked=b.optBoolean("allow_as_secondary",true); bwWeight.setText(b.optDouble("weight",0.25).toString())
        sampleAlphaMin.setText(config.optInt("sample_alpha_min",48).toString()); minimumColorDistance.setText(config.optDouble("minimum_color_distance",42.0).toString())
        whiteLuminanceMin.setText(b.optDouble("white_luminance_min",0.92).toString()); whiteSaturationMax.setText(b.optDouble("white_saturation_max",0.10).toString()); blackLuminanceMax.setText(b.optDouble("black_luminance_max",0.08).toString())
        skinHueMin.setText(s.optDouble("hue_min",5.0).toString()); skinHueMax.setText(s.optDouble("hue_max",48.0).toString())
        skinSaturationMin.setText(s.optDouble("saturation_min",0.10).toString()); skinSaturationMax.setText(s.optDouble("saturation_max",0.78).toString()); skinValueMin.setText(s.optDouble("value_min",0.30).toString())
    }
    private fun showPreferences(){
        loadingPreferences=true
        val manual=preferences.optJSONObject("manual")?:JSONObject()
        manualInitialized=preferences.optBoolean("manual_initialized",false); appFollowTheme.isChecked=preferences.optBoolean("app_follow_theme",true)
        manualPrimaryHex=validHex(manual.optString("primary"),"#48A4F2"); manualSecondaryHex=validHex(manual.optString("secondary"),"#AEDCFA"); manualTextHex=validHex(manual.optString("text"),"#FFFFFF"); manualBubbleTextHex=validHex(manual.optString("bubble_text"),manualTextHex)
        when(preferences.optString("mode","auto")){"manual"->manualMode.isChecked=true;"classic"->classicMode.isChecked=true;else->autoMode.isChecked=true}
        loadingPreferences=false
        paintChoice(manualPrimary,"主题色一",manualPrimaryHex); paintChoice(manualSecondary,"主题色二",manualSecondaryHex); paintChoice(manualText,"字体颜色",manualTextHex); paintChoice(manualBubbleText,"对话文字颜色",manualBubbleTextHex); updateManualEnabled()
    }
    private fun validHex(value:String,fallback:String)=if(Regex("^#[0-9A-Fa-f]{6}$").matches(value))value.uppercase() else fallback
    private fun showTheme(j:JSONObject){currentTheme=j;val s=j.optJSONObject("source")?:j;paint(primary,"主色",s.optString("primary","#4D78C8"));paint(secondary,"次色",s.optString("secondary","#F4F6FA"))}
    private fun paint(v:TextView,n:String,hex:String){val c=runCatching{Color.parseColor(hex)}.getOrDefault(Color.GRAY);v.text="$n：$hex";v.setTextColor(contrastText(c));v.background=GradientDrawable().apply{cornerRadius=dp(6).toFloat();setColor(c);setStroke(dp(1),Color.GRAY)}}
    private fun paintChoice(v:TextView,n:String,hex:String){paint(v,"$n（点击选择）",hex)}
    private fun contrastText(color:Int):Int { val y=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;return if(y>=150)Color.BLACK else Color.WHITE }
    private fun updateManualEnabled(){if(!::manualMode.isInitialized||!::manualPrimary.isInitialized)return;val enabled=manualMode.isChecked&&!busy;listOf(manualPrimary,manualSecondary,manualText,manualBubbleText).forEach{it.isEnabled=enabled;it.alpha=if(enabled)1f else .48f}}
    private fun copyCurrentThemeToManual(markInitialized:Boolean){
        if(currentTheme.length()==0){toast("当前主题仍在读取，请稍后重试");return}
        val source=currentTheme.optJSONObject("source")?:currentTheme
        val resolved=currentTheme.optJSONObject("resolved")?:JSONObject()
        manualPrimaryHex=validHex(source.optString("primary"),manualPrimaryHex)
        manualSecondaryHex=validHex(source.optString("secondary"),manualSecondaryHex)
        manualTextHex=validHex(resolved.optString("text",source.optString("text")),manualTextHex)
        manualBubbleTextHex=validHex(resolved.optString("bubble_text",source.optString("bubble_text")),manualTextHex)
        if(markInitialized)manualInitialized=true
        paintChoice(manualPrimary,"主题色一",manualPrimaryHex);paintChoice(manualSecondary,"主题色二",manualSecondaryHex);paintChoice(manualText,"字体颜色",manualTextHex);paintChoice(manualBubbleText,"对话文字颜色",manualBubbleTextHex)
    }
    private fun pickColor(titleText:String,initial:String,onChosen:(String)->Unit){
        val initialColor=runCatching{Color.parseColor(initial)}.getOrDefault(Color.WHITE)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),0)}
        val preview=TextView(this).apply{textSize=18f;gravity=Gravity.CENTER;setPadding(dp(8),dp(14),dp(8),dp(14))}.also{box.addView(it)}
        val values=intArrayOf(Color.red(initialColor),Color.green(initialColor),Color.blue(initialColor))
        val bars=arrayOfNulls<SeekBar>(3);val numbers=arrayOfNulls<TextView>(3)
        fun redraw(){val color=Color.rgb(values[0],values[1],values[2]);preview.text=String.format("#%02X%02X%02X",values[0],values[1],values[2]);preview.setTextColor(contrastText(color));preview.setBackgroundColor(color)}
        fun select(hex:String){val color=runCatching{Color.parseColor(hex)}.getOrNull()?:return;values[0]=Color.red(color);values[1]=Color.green(color);values[2]=Color.blue(color);values.indices.forEach{bars[it]?.progress=values[it];numbers[it]?.text=values[it].toString()};redraw()}
        box.addView(label("快捷色板"))
        val source=currentTheme.optJSONObject("source")?:currentTheme;val resolved=currentTheme.optJSONObject("resolved")?:JSONObject()
        val presets=listOf(
            "当前主色" to validHex(source.optString("primary"),manualPrimaryHex),"当前次色" to validHex(source.optString("secondary"),manualSecondaryHex),"当前字体" to validHex(resolved.optString("text",source.optString("text")),manualTextHex),"当前对话" to validHex(resolved.optString("bubble_text",source.optString("bubble_text")),manualBubbleTextHex),
            "黑" to "#000000","白" to "#FFFFFF","灰" to "#808080","浅灰" to "#D9D9D9","红" to "#FF0000","深红" to "#C00000","橙" to "#ED7D31","黄" to "#FFFF00","浅绿" to "#92D050","绿" to "#00B050","青" to "#00B0F0","浅蓝" to "#5B9BD5","蓝" to "#4472C4","紫" to "#7030A0","粉" to "#FF66CC","棕" to "#843C0C")
        presets.chunked(5).forEach{group->box.addView(LinearLayout(this@CharacterSettingsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;group.forEach{(name,hex)->addView(TextView(this@CharacterSettingsActivity).apply{tag=AppThemeManager.SKIP_TAG;contentDescription=name+" "+hex;text=when(name){"当前主色"->"主";"当前次色"->"次";"当前字体"->"字";"当前对话"->"话";else->""};gravity=Gravity.CENTER;setTextColor(contrastText(Color.parseColor(hex)));background=GradientDrawable().apply{cornerRadius=dp(4).toFloat();setColor(Color.parseColor(hex));setStroke(dp(1),Color.DKGRAY)};setOnClickListener{select(hex)}},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})}})}
        box.addView(label("RGB 精确调色"))
        listOf("R","G","B").forEachIndexed{i,name->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(this).apply{text=name;gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(30),ViewGroup.LayoutParams.WRAP_CONTENT))
            numbers[i]=TextView(this).apply{text=values[i].toString();gravity=Gravity.CENTER}.also{row.addView(it,LinearLayout.LayoutParams(dp(48),ViewGroup.LayoutParams.WRAP_CONTENT))}
            bars[i]=SeekBar(this).apply{max=255;progress=values[i];setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,from:Boolean){values[i]=p;numbers[i]?.text=p.toString();redraw()};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})}
            row.addView(bars[i],LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));box.addView(row)
        };redraw()
        val dialog=AlertDialog.Builder(this).setTitle(titleText).setView(ScrollView(this).apply{addView(box)}).setNegativeButton("取消",null).setPositiveButton("确定",null).create()
        colorDialog=dialog;dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{onChosen(String.format("#%02X%02X%02X",values[0],values[1],values[2]));dialog.dismiss()}};dialog.show()
    }
    private fun operate(ok:String, block:suspend()->Unit){if(busy)return;busy=true;updateManualEnabled();scope.launch{try{withContext(Dispatchers.IO){block()};toast(ok);val t=withContext(Dispatchers.IO){PythonBridge.getCharacterThemeJson(this@CharacterSettingsActivity)};showTheme(JSONObject(t));AppThemeManager.apply(this@CharacterSettingsActivity)}catch(e:Exception){toast("操作失败：${e.message}")}finally{busy=false;updateManualEnabled()}}}
    private fun ensure(raw:String):String{val j=JSONObject(raw);if(j.has("success")&&!j.optBoolean("success"))error(j.optString("error","Python 操作失败"));return raw}
    private fun rerender(){val p=PythonBridge.renderWidgetPng(this)?:error("小组件渲染失败");WidgetRefreshController.publishRenderedImageToAll(this,p,false)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onDestroy(){colorDialog?.dismiss();colorDialog=null;scope.cancel();super.onDestroy()}
}
