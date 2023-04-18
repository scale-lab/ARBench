import urllib.request
import tarfile
import tempfile
import os

part1_id = "1gezseWIM5kDVfbsE6ySJp2CY1Qnb7tzO"
part2_id = "19o8ccmWU6gSAjAttRJVgOrcT3rqqFX3D"
part3_id = "1WURjbqzgGbfVf35hOTvduFcs6YWTmwtK"
extraction_path_1_and_2 = "benchmark/app/src/main/assets/recordings"
extraction_path_3 = "benchmark/app/src/main/assets/tessdata"


def download_and_extract(file_id, extraction_path):
    url = "https://drive.google.com/uc?id=" + file_id
    with tempfile.NamedTemporaryFile(delete=False) as tf:
        with urllib.request.urlopen(url) as response:
            tf.write(response.read())
            tar_path = tf.name
    tar = tarfile.open(tar_path, "r")
    tar.extractall(path=extraction_path)
    tar.close()
    os.remove(tar_path)


print("Downloading part 1/3")
download_and_extract(part1_id, extraction_path_1_and_2)
print("Downloading part 2/3")
download_and_extract(part2_id, extraction_path_1_and_2)
print("Downloading part 3/3")
download_and_extract(part3_id, extraction_path_3)
print("Done")
