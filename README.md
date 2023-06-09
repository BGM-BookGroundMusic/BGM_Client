# 🎵 Book Ground Music
전자책 텍스트 분석을 통해 실시간으로 분위기에 맞는 배경음악을 제공해주는 백그라운드 어플리케이션입니다.

## Step 1. 전자책 텍스트 데이터 수집
전자책의 데이터를 실시간으로 수집하기 위해, OCR을 사용합니다. 더 구체적으로는 Firebase ML Kit가 제공하는 Text Recognizer를 사용하였으며, 실시간으로 백그라운드에서 스크린샷을 얻어와 해당 비트맵을 OCR을 활용하여 텍스트 데이터를 얻습니다.  
수집된 텍스트 데이터를 바탕으로 감성분석을 진행합니다.  

## Step 2. 


## Step 3. 감성 음악 매칭
앞선 단계에서 얻은 최종감성에 매칭하는 음악을 사용자에게 들려줍니다. 
