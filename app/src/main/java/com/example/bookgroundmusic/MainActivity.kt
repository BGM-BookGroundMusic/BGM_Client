package com.example.bookgroundmusic

import android.content.Intent
import android.graphics.Color
import android.icu.number.IntegerWidth
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bookgroundmusic.DataClass.SentimentAnalysisResponse
import com.example.bookgroundmusic.databinding.ActivityMainBinding
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.FileOutputStream
import java.text.DecimalFormat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random


class MainActivity : AppCompatActivity() {
    private var mBinding: ActivityMainBinding? = null
    private val binding get() = mBinding!!
    private val gson = Gson()

    // 음악 재생 중인지 판별용
    var isPaused = true
    var isPlayed = false    // resume용 변수

    var player = MediaPlayer()

    // 음악 플레이리스트 초기화
    var playlist: ArrayList<Int> = ArrayList()
    var currentIndex = 0    // 다음 곡 재생용

    // 음악 랜덤으로 선택용
    var randomIndex = 0

    // 예민도 모드 확인용
    var lowSensitive = true
    var highSensitive = false

    // 장르 확인용
    var asmr = false
    var lofi = false
    var jazz = false
    var cl = false

    // 텍스트 감성 저장용
    var sentiment = ""
    // 5번의 연속적인 감성 넣기
    var sentimentList: ArrayList<String> = ArrayList()

    private val handler = Handler(Looper.getMainLooper())
    private val handler2 = Handler()

    // 곡 남은 시간 확인
    private val checkRemainTimeRunnable = object : Runnable {
        override fun run() {
            val total_duration = player.duration
            val remainingTime = total_duration - player.currentPosition
            val remainingSecs = remainingTime / 1000
            val df = DecimalFormat("#")
            val formattedRemainingTime = df.format(remainingSecs)

            // 남은 시간이 80초일 때 스크린샷
            if (remainingSecs == 80) {
                Log.d("WJ", formattedRemainingTime + "초 남음")    // 성공
                screenshotSeries()
            }
            handler.postDelayed(this, 1000) // run every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modeCheck()
        genreCheck()

        // playlist 초기화 (무조건 중립음악으로 시작)
        //
        // 여기서 에러 수정할 것
        playlist.add(R.raw.neutral_lofi1)
        //addNeutralSong()

        //permissionCheck()
        startMainService()
        //initializeView()
        setListener()

    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        stopCheckingRemainTime()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MediaProjectionController.mediaScreenCapture -> {
                MediaProjectionController.getMediaProjectionCapture(this, resultCode, data)
            }
        }
    }

    private fun startMainService() {

        val serviceIntent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

    }

    private fun stopMainService() {
        val serviceIntent = Intent(this, MainService::class.java)
        stopService(serviceIntent)
    }

    // 음악 남은 시간 알아내기
    private fun startCheckingRemainTime() {
        handler.postDelayed(checkRemainTimeRunnable, 1000) // start checking after 1 second
    }

    private fun stopCheckingRemainTime() {
        handler.removeCallbacks(checkRemainTimeRunnable)
    }


    // 음악 재생, 일시 정지
    fun musicControl() {
        // 재생 안되고 있는 상태 => play 시키기
        if (isPaused) {
            if (isPlayed) {
                player.start()
            }

            // 현재 재생 중인 곡이 끝나면 다음 곡 재생하는 코드
            player.setOnCompletionListener {
                currentIndex = (currentIndex + 1) % playlist.size
                player.reset()
                player.setDataSource(resources.openRawResourceFd(playlist[currentIndex]))
                player.prepare()
                player.start()
            }
            player.start()

            isPaused = false
            isPlayed = true
            Toast.makeText(this, "재생 시작함", Toast.LENGTH_LONG).show()

            startCheckingRemainTime()
        }

        // 재생 중인 상태 => pause 시키기
        else if (!isPaused) {
            player.pause()
            isPaused = true

            Toast.makeText(this, "일시정지함", Toast.LENGTH_LONG).show()
        }
    }


    // 일정 시간 간격 연속 스크린샷
    private fun screenshotSeries() {
        // OCR (ML Kit)
        val options = KoreanTextRecognizerOptions.Builder().build()
        val recognizer = TextRecognition.getClient(options)
        val intervalMillis = 15000

        // 연속 5번 스크린샷
        var i = 1
        //val handler = Handler()
        handler2.postDelayed(object : Runnable {
            override fun run() {
                if (i in 1..5) {
                    // 백그라운드 캡처
                    MediaProjectionController.screenCapture(this@MainActivity) { bitmap ->

                        val image = InputImage.fromBitmap(bitmap, 0)

                        val result = recognizer.process(image)
                             .addOnSuccessListener { visionText ->
                                // 텍스트 결과 & 감성분석 결과
                                Log.d("WJ", visionText.text.toString())

                                sentiment = callSentimentAnalysisAPI(visionText.text.toString())
                                 Log.d("WJ", sentiment)

                                 if (sentiment != "") {
                                     sentimentList.add(sentiment)
                                 }

                                 // Check if we have collected five sentiments
                                 if (sentimentList.size >= 5) {
                                     handler2.removeCallbacksAndMessages(null)
                                     // Call the decision function with the sentimentList
                                     sentimentDecision()
                                 }

                    }
                    .addOnFailureListener { e -> }
                    }

                    i++
                    handler2.postDelayed(this, intervalMillis.toLong())
                }
            }
        }, intervalMillis.toLong())

    }

    // 음악 감성 결정 알고리즘 -> 시간 고려한 가중치 (현재 ~ 과거)
    private fun sentimentDecision() {
        var sum = 0.0
        Log.d("WJ", sentimentList[0] + ", " + sentimentList[1] + ", " + sentimentList[2] + ", " + sentimentList[3] + ", " + sentimentList[4])

//        if (probabilityOfItem(sentimentList, "positive") > 50) {
//            Log.d("WJ", probabilityOfItem(sentimentList, "positive").toString())
//            addPositiveSong()
//        }
//        else if (probabilityOfItem(sentimentList, "negative") > 50) {
//            Log.d("WJ", probabilityOfItem(sentimentList, "negative").toString())
//            addNegativeSong()
//        }
//        else {
//            Log.d("WJ", probabilityOfItem(sentimentList, "neutral").toString())
//            addNeutralSong()
//        }

        // 시간에 따라 가중치를 다르게 주는 과정
        when (sentimentList[0]) {
            "positive" -> sum += 0.1
            "negative" -> sum -= 0.1
        }

        when (sentimentList[1]) {
            "positive" -> sum += 0.3
            "negative" -> sum -= 0.3
        }

        when (sentimentList[2]) {
            "positive" -> sum += 0.5
            "negative" -> sum -= 0.5
        }

        when (sentimentList[3]) {
            "positive" -> sum += 0.7
            "negative" -> sum -= 0.7
        }

        when (sentimentList[4]) {
            "positive" -> sum += 0.9
            "negative" -> sum -= 0.9
        }

        Log.d("WJ", "감성 총값 : $sum")

        // 음악 선정 과정
        if (sum == 0.0) {
            addNeutralSong()
        } else if (sum > 0.0) {
            addPositiveSong()
        } else {
            addNegativeSong()
        }

        sentimentList.clear()
    }

    // 감성확률 구하기 (5개 중에 비율, 연속성 고려 X)
    private fun probabilityOfItem(arr: ArrayList<String>, target: String): Double {
        var cnt = arr.count { it == target }
        return (cnt.toDouble() / arr.size) * 100
    }

    //감성분석 Clova sentiment
    private fun callSentimentAnalysisAPI(text: String) : String {
        val url = "https://naveropenapi.apigw.ntruss.com/sentiment-analysis/v1/analyze"
        val client = OkHttpClient()
        val jsonBody = JSONObject()
            .put("content", text)
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
        val request = Request.Builder()
            .url(url)
            .addHeader("X-NCP-APIGW-API-KEY-ID", "hzpldolb38")
            .addHeader("X-NCP-APIGW-API-KEY", "xRLUPCPWT8XpNDWXOQOgiSgKxz1XNpgcjMYixloY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // API 호출 실패 처리
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                // API 호출 성공 처리
                val jsonResult = response.body?.string()
                val responseObject = gson.fromJson(jsonResult, SentimentAnalysisResponse::class.java)
                Log.d("API Response", jsonResult ?: "")

                if (responseObject.document == null){
                    Log.d("ERROR","분석이 제대로 완료되지 않았습니다.")
                }

                if (responseObject.document != null) {
                    // 전체 문장 감정 정보 로그 출력
//                    Log.d("WJ", "전체 문장 감정: ${responseObject.document.sentiment}")
//                    Log.d("WJ", "중립 감정 확률: ${responseObject.document.confidence.neutral}")
//                    Log.d("WJ", "긍정 감정 확률: ${responseObject.document.confidence.positive}")
//                    Log.d("WJ", "부정 감정 확률: ${responseObject.document.confidence.negative}")
                    sentiment = responseObject.document.sentiment

                }

                /* 나중에 필요하면 쓰깅~~~
                if (responseObject.sentences != null) {
                    for (sentence in responseObject.sentences) {
                        if (sentence.sentiment != null) {
                            // 분류 문장 감정 정보 로그 출력
                            Log.d("MainActivity", "분류 문장: ${sentence.content}")
                            Log.d("MainActivity", "문장 감정: ${sentence.sentiment}")
                            Log.d("MainActivity", "중립 감정 확률: ${sentence.confidence.neutral}")
                            Log.d("MainActivity", "긍정 감정 확률: ${sentence.confidence.positive}")
                            Log.d("MainActivity", "부정 감정 확률: ${sentence.confidence.negative}")
                        }
                    }
                }
                 */
            }
        })
        return sentiment
    }

    private fun setListener() {
        // 1. 시작하기 버튼 클릭시
        binding.btnOn.setOnClickListener {

            // 버튼 클릭 시 텍스트 변환
            if (binding.btnOn.text == "시작하기") {
                // 모드 or 장르 체크 안 했을 경우
                if ((!lowSensitive && !highSensitive) || (!asmr && !lofi && !jazz && !cl)) {
                    Toast.makeText(this, "민감도 및 장르 설정을 모두 완료하였는지 다시 한번 확인해주세요.", Toast.LENGTH_LONG).show()
               }
                else {
                    binding.btnOn.setText("중 지")
                    Toast.makeText(this, "지금부터 분석을 시작합니다.\n잠시 후, 책의 분위기에 어울리는 노래를 들려드립니다!", Toast.LENGTH_LONG).show()
                    musicControl()
                }
            } else {
                binding.btnOn.setText("시작하기")
                Toast.makeText(this, "분석을 중지합니다.\n'시작하기' 버튼을 다시 누르면, 배경음악이 다시 재생됩니다.", Toast.LENGTH_LONG).show()
                musicControl()
            }

            }


        // 2. 사용 설명서 화면으로 이동
        binding.buttonGuide.setOnClickListener {
            val intent = Intent(this, GuideActivity::class.java)
            startActivity(intent)
        }

    }

    // 민감도 모드 확인
    private fun modeCheck() {
        binding.mode1.setOnClickListener {
            if (!lowSensitive) {
                if (highSensitive) {
                    highSensitive = false
                    binding.mode2.setTextColor(Color.parseColor("#000000"))
                }
                binding.mode1.setTextColor(Color.parseColor("#FF730D"))
                lowSensitive = true
            } else {
                binding.mode1.setTextColor(Color.parseColor("#000000"))
                lowSensitive = false
            }
        }

        binding.mode2.setOnClickListener {
            if (!highSensitive) {
                if (lowSensitive) {
                    lowSensitive = false
                    binding.mode1.setTextColor(Color.parseColor("#000000"))
                }
                binding.mode2.setTextColor(Color.parseColor("#FF730D"))
                highSensitive = true
            } else {
                binding.mode2.setTextColor(Color.parseColor("#000000"))
                highSensitive = false
            }
        }

    }

    // 장르 확인
    private fun genreCheck() {
        binding.genre1.setOnClickListener {
            if (!asmr) {
                if (lofi || jazz || cl) {
                    lofi = false
                    jazz = false
                    cl = false
                    binding.genre2.setTextColor(Color.parseColor("#000000"))
                    binding.genre3.setTextColor(Color.parseColor("#000000"))
                    binding.genre4.setTextColor(Color.parseColor("#000000"))
                }
                binding.genre1.setTextColor(Color.parseColor("#FF730D"))
                asmr = true
            } else {
                binding.genre1.setTextColor(Color.parseColor("#000000"))
                asmr = false
            }
        }

        binding.genre2.setOnClickListener {
            if (!lofi) {
                if (asmr || jazz || cl) {
                    asmr = false
                    jazz = false
                    cl = false
                    binding.genre1.setTextColor(Color.parseColor("#000000"))
                    binding.genre3.setTextColor(Color.parseColor("#000000"))
                    binding.genre4.setTextColor(Color.parseColor("#000000"))
                }
                binding.genre2.setTextColor(Color.parseColor("#FF730D"))
                lofi = true
            } else {
                binding.genre2.setTextColor(Color.parseColor("#000000"))
                lofi = false
            }
        }

        binding.genre3.setOnClickListener {
            if (!jazz) {
                if (lofi || asmr || cl) {
                    lofi = false
                    asmr = false
                    cl = false
                    binding.genre2.setTextColor(Color.parseColor("#000000"))
                    binding.genre1.setTextColor(Color.parseColor("#000000"))
                    binding.genre4.setTextColor(Color.parseColor("#000000"))
                }
                binding.genre3.setTextColor(Color.parseColor("#FF730D"))
                jazz = true
            } else {
                binding.genre3.setTextColor(Color.parseColor("#000000"))
                jazz = false
            }
        }

        binding.genre4.setOnClickListener {
            if (!cl) {
                if (lofi || jazz || asmr) {
                    lofi = false
                    jazz = false
                    asmr = false
                    binding.genre2.setTextColor(Color.parseColor("#000000"))
                    binding.genre3.setTextColor(Color.parseColor("#000000"))
                    binding.genre1.setTextColor(Color.parseColor("#000000"))
                }
                binding.genre4.setTextColor(Color.parseColor("#FF730D"))
                cl = true
            } else {
                binding.genre4.setTextColor(Color.parseColor("#000000"))
                cl = false
            }
        }
    }


    // 랜덤하게 음악 추가 = 각각 중립, 긍정, 부정
    private fun addNeutralSong() {
        // 취향 나누기 (asmr, lofi, jazz, cl)
        if (asmr) {
            randomIndex = Random.nextInt(4) + 1
            when (randomIndex) {
                1 -> playlist.add(R.raw.neutral_nature1)
                2 -> playlist.add(R.raw.neutral_nature2)
                3 -> playlist.add(R.raw.neutral_nature3)
                4 -> playlist.add(R.raw.neutral_nature4)
            }
            // 확인용
            Log.d("WJ", "중립 > asmr 곡 들어감")
        }

        else if (lofi) {
            randomIndex = Random.nextInt(4) + 1
            when (randomIndex) {
                1 -> playlist.add(R.raw.neutral_lofi1)
                2 -> playlist.add(R.raw.neutral_lofi2)
                3 -> playlist.add(R.raw.neutral_lofi3)
                4 -> playlist.add(R.raw.neutral_lofi4)
            }
            // 확인용
            Log.d("WJ", "중립 > lofi 곡 들어감")
        }

        else if (jazz) {
            randomIndex = Random.nextInt(3) + 1
            when (randomIndex) {
                1 -> playlist.add(R.raw.neutral_jazz1)
                2 -> playlist.add(R.raw.neutral_jazz2)
                3 -> playlist.add(R.raw.neutral_jazz3)
            }
            // 확인용
            Log.d("WJ", "중립 > jazz 곡 들어감")
        }

        else if (cl) {
            randomIndex = Random.nextInt(5) + 1
            when (randomIndex) {
                1 -> playlist.add(R.raw.neutral_classical1)
                2 -> playlist.add(R.raw.neutral_classical2)
                3 -> playlist.add(R.raw.neutral_classical3)
                4 -> playlist.add(R.raw.neutral_classical4)
                5 -> playlist.add(R.raw.neutral_classical5)
            }
            // 확인용
            Log.d("WJ", "중립 > classical 곡 들어감")
        }

    }

    private fun addPositiveSong() {
        Log.d("WJ", "긍정곡 추가")
        randomIndex = Random.nextInt(7) + 1
        when (randomIndex) {
            1 -> playlist.add(R.raw.pos1)
            2 -> playlist.add(R.raw.pos2)
            3 -> playlist.add(R.raw.pos3)
            4 -> playlist.add(R.raw.pos4)
            5 -> playlist.add(R.raw.pos5)
            6 -> playlist.add(R.raw.pos6)
            7 -> playlist.add(R.raw.pos7)
        }
    }

    private fun addNegativeSong() {
        Log.d("WJ", "부정곡 추가")
        randomIndex = Random.nextInt(10) + 1
        when (randomIndex) {
            1 -> playlist.add(R.raw.neg1)
            2 -> playlist.add(R.raw.neg2)
            3 -> playlist.add(R.raw.neg3)
            4 -> playlist.add(R.raw.neg4)
            5 -> playlist.add(R.raw.neg5)
            6 -> playlist.add(R.raw.neg6)
            7 -> playlist.add(R.raw.neg7)
            8 -> playlist.add(R.raw.neg8)
            9 -> playlist.add(R.raw.neg9)
            10 -> playlist.add(R.raw.neg10)
        }
    }

}