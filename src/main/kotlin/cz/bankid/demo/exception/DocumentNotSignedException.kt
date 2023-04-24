package cz.bankid.demo.exception

class DocumentNotSignedException() : Exception(message) {
    companion object {
        val message = "Document is not signed or doesn't contain Bank iD signature"
    }
}